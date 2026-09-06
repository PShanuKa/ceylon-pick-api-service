package lk.ceylonpick.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lk.ceylonpick.auth.AuthProperties;
import lk.ceylonpick.auth.api.UserStatus;
import lk.ceylonpick.auth.domain.AppUser;
import lk.ceylonpick.auth.domain.AuditLogEntry;
import lk.ceylonpick.auth.domain.RefreshToken;
import lk.ceylonpick.auth.repo.AppUserRepository;
import lk.ceylonpick.shared.web.ApiException;

/**
 * Owns the failed-login lockout (FR-AUTH-02) and every change to a user's
 * status.
 *
 * <p>This is the only place status is written, and every path through it evicts
 * the cached {@link lk.ceylonpick.auth.api.AuthUser}. Later modules that
 * deactivate people on a schedule — creator auto-pause at RTO &gt; 30% (BR-16),
 * vendor suspension on the third strike (BR-23) — must go through here rather
 * than updating the row directly, or a disabled account keeps working until the
 * cache TTL expires.
 */
@Service
public class AccountService {

    private final AppUserRepository users;
    private final AuthUserService authUsers;
    private final TokenService tokens;
    private final AuditService audit;
    private final AuthProperties properties;
    private final Clock clock;

    public AccountService(AppUserRepository users,
                          AuthUserService authUsers,
                          TokenService tokens,
                          AuditService audit,
                          AuthProperties properties,
                          Clock clock) {
        this.users = users;
        this.authUsers = authUsers;
        this.tokens = tokens;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * FR-AUTH-02: "5 failed attempts -> 15-min lockout".
     *
     * <p>Runs in its own transaction because the caller throws immediately
     * afterwards; without that, the rollback would discard the very increment
     * that enforces the limit.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedLogin(String userId, String ip) {
        AppUser user = users.findById(userId).orElse(null);
        if (user == null) {
            return;
        }
        Instant now = clock.instant();
        int attempts = user.getFailedLogins() + 1;
        user.setFailedLogins(attempts);

        if (attempts >= properties.lockout().maxFailedAttempts()) {
            user.setLockedUntil(now.plus(properties.lockout().duration()));
            user.setFailedLogins(0);
            audit.record(AuditLogEntry.ACCOUNT_LOCKED, user.getId(), user.getRole().name(),
                    "app_user", user.getId(), ip);
        }
        users.save(user);
        authUsers.evict(user.getId());
    }

    /** Clears the lockout counters and stamps the sign-in. */
    @Transactional
    public void recordSuccessfulLogin(AppUser user) {
        user.setFailedLogins(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(clock.instant());
        users.save(user);
        authUsers.evict(user.getId());
    }

    /** Throws if this account cannot sign in right now, without saying which reason. */
    public void assertCanAuthenticate(AppUser user) {
        if (user.getStatus() == UserStatus.DISABLED) {
            throw ApiException.forbidden("ACCOUNT_UNAVAILABLE", "This account is not active");
        }
        if (user.getStatus() == UserStatus.LOCKED) {
            throw ApiException.locked("ACCOUNT_LOCKED", "This account is locked. Contact support.");
        }
        if (user.isTemporarilyLocked(clock.instant())) {
            throw ApiException.locked("ACCOUNT_LOCKED", "Too many failed attempts. Try again later.");
        }
    }

    /**
     * Changes status, ends every session when the account stops being usable,
     * and evicts the cache so the change lands on the next request.
     */
    @Transactional
    public AppUser changeStatus(String userId, UserStatus newStatus, String actorId,
                                String actorRole, String reason, String ip) {
        AppUser user = users.findById(userId)
                .orElseThrow(() -> ApiException.badRequest("UNKNOWN_USER", "No such user"));
        UserStatus previous = user.getStatus();
        if (previous == newStatus) {
            return user;
        }
        user.setStatus(newStatus);
        if (newStatus == UserStatus.ACTIVE) {
            user.setFailedLogins(0);
            user.setLockedUntil(null);
        } else {
            tokens.invalidateAllSessions(user, RefreshToken.REASON_SESSIONS_INVALIDATED);
        }
        users.save(user);
        authUsers.evict(userId);

        audit.record(AuditLogEntry.USER_STATUS_CHANGED, actorId, actorRole,
                "app_user", userId, ip,
                Map.of("status", previous.name()),
                Map.of("status", newStatus.name()),
                reason);
        return user;
    }

    /** Signs the user out of every device and invalidates outstanding access tokens. */
    @Transactional
    public void invalidateAllSessions(String userId, String actorId, String actorRole, String ip) {
        AppUser user = users.findById(userId)
                .orElseThrow(() -> ApiException.badRequest("UNKNOWN_USER", "No such user"));
        tokens.invalidateAllSessions(user, RefreshToken.REASON_SESSIONS_INVALIDATED);
        authUsers.evict(userId);
        audit.record(AuditLogEntry.SESSIONS_INVALIDATED, actorId, actorRole, "app_user", userId, ip);
    }
}
