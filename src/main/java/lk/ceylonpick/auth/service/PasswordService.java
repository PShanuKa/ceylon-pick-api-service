package lk.ceylonpick.auth.service;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lk.ceylonpick.auth.AuthProperties;
import lk.ceylonpick.auth.domain.AppUser;
import lk.ceylonpick.auth.domain.AuditLogEntry;
import lk.ceylonpick.auth.domain.PasswordResetToken;
import lk.ceylonpick.auth.domain.RefreshToken;
import lk.ceylonpick.auth.repo.AppUserRepository;
import lk.ceylonpick.auth.repo.PasswordResetTokenRepository;
import lk.ceylonpick.shared.web.ApiException;
import lk.ceylonpick.shared.Hashes;
import lk.ceylonpick.shared.Ids;

/**
 * Forgotten-password and password-change flows.
 *
 * <p>Not in the SRS at all, but unavoidable once staff and customers hold
 * passwords. Both flows end by invalidating every session for the account: if a
 * password was reset because it leaked, the sessions opened with it must go too.
 */
@Service
public class PasswordService {

    private static final Logger log = LoggerFactory.getLogger(PasswordService.class);

    private final AppUserRepository users;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final TokenService tokens;
    private final AuthUserService authUsers;
    private final AuditService audit;
    private final AuthProperties properties;
    private final Clock clock;

    public PasswordService(AppUserRepository users,
                           PasswordResetTokenRepository resetTokens,
                           PasswordEncoder passwordEncoder,
                           PasswordPolicy passwordPolicy,
                           TokenService tokens,
                           AuthUserService authUsers,
                           AuditService audit,
                           AuthProperties properties,
                           Clock clock) {
        this.users = users;
        this.resetTokens = resetTokens;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.tokens = tokens;
        this.authUsers = authUsers;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Always succeeds from the caller's point of view, whether or not the
     * address is registered — otherwise this endpoint becomes an account
     * directory.
     */
    @Transactional
    public void requestReset(String rawEmail, String ip) {
        String email = AppUser.normaliseEmail(rawEmail);
        if (email == null) {
            return;
        }
        users.findByEmail(email).filter(AppUser::hasPassword).ifPresent(user -> {
            Instant now = clock.instant();
            String raw = Hashes.randomToken(32);

            PasswordResetToken token = new PasswordResetToken();
            token.setId(Ids.newId());
            token.setUserId(user.getId());
            token.setTokenHash(Hashes.sha256(raw));
            token.setExpiresAt(now.plus(properties.reset().passwordTokenTtl()));
            token.setIpHash(Hashes.ipHash(ip));
            token.setCreatedAt(now);
            resetTokens.save(token);

            // Replace with the notifications module once it exists (Architecture §4).
            log.warn("No mailer configured. Password reset token for {} is {}", email, raw);
        });
    }

    @Transactional
    public void reset(String rawToken, String newPassword, String ip) {
        Instant now = clock.instant();
        PasswordResetToken token = resetTokens.findByTokenHash(Hashes.sha256(rawToken))
                .orElseThrow(() -> ApiException.badRequest("INVALID_TOKEN", "That link is not valid"));
        if (!token.isUsable(now)) {
            throw ApiException.badRequest("INVALID_TOKEN", "That link has expired or was already used");
        }
        AppUser user = users.findById(token.getUserId())
                .orElseThrow(() -> ApiException.badRequest("INVALID_TOKEN", "That link is not valid"));

        passwordPolicy.validate(newPassword, user.getEmail());
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setFailedLogins(0);
        user.setLockedUntil(null);
        tokens.invalidateAllSessions(user, RefreshToken.REASON_SESSIONS_INVALIDATED);
        users.save(user);

        token.setUsedAt(now);
        resetTokens.save(token);
        authUsers.evict(user.getId());

        audit.record(AuditLogEntry.PASSWORD_RESET, user.getId(), user.getRole().name(),
                "app_user", user.getId(), ip);
    }

    /** Signs the user out everywhere, including the session that made the change. */
    @Transactional
    public void change(String userId, String currentPassword, String newPassword, String ip) {
        AppUser user = users.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("INVALID_SESSION", "Session is not valid"));
        if (!user.hasPassword() || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw ApiException.badRequest("INVALID_CREDENTIALS", "Current password is incorrect");
        }
        passwordPolicy.validate(newPassword, user.getEmail());

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        tokens.invalidateAllSessions(user, RefreshToken.REASON_SESSIONS_INVALIDATED);
        users.save(user);
        authUsers.evict(userId);

        audit.record(AuditLogEntry.PASSWORD_CHANGED, userId, user.getRole().name(),
                "app_user", userId, ip);
    }
}
