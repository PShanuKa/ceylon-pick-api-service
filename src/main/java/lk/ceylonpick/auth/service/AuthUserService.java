package lk.ceylonpick.auth.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lk.ceylonpick.auth.api.AuthUser;
import lk.ceylonpick.auth.api.Role;
import lk.ceylonpick.auth.cache.AuthUserCache;
import lk.ceylonpick.auth.domain.AdminProfile;
import lk.ceylonpick.auth.domain.AppUser;
import lk.ceylonpick.auth.repo.AdminProfileRepository;
import lk.ceylonpick.auth.repo.AppUserRepository;

/**
 * The single way to obtain an {@link AuthUser}, and the single place that
 * evicts one.
 *
 * <p>Every code path that changes a user's role, admin tier or status must call
 * {@link #evict(String)} in the same transaction — the automatic failed-login
 * lockout and the scheduled jobs that pause creators (BR-16) and suspend
 * vendors (BR-23) included, not just the admin endpoints.
 */
@Service
public class AuthUserService {

    private final AppUserRepository users;
    private final AdminProfileRepository adminProfiles;
    private final AuthUserCache cache;

    public AuthUserService(AppUserRepository users,
                           AdminProfileRepository adminProfiles,
                           AuthUserCache cache) {
        this.users = users;
        this.adminProfiles = adminProfiles;
        this.cache = cache;
    }

    /** Cache-first; falls through to the database on a miss or after an eviction. */
    @Transactional(readOnly = true)
    public Optional<AuthUser> find(String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(cache.get(userId, this::load));
    }

    public void evict(String userId) {
        cache.evict(userId);
    }

    public void evictAll() {
        cache.evictAll();
    }

    /** Rebuilds the snapshot and puts it back, e.g. straight after a login. */
    @Transactional(readOnly = true)
    public Optional<AuthUser> refresh(String userId) {
        cache.evict(userId);
        return find(userId);
    }

    private AuthUser load(String userId) {
        AppUser user = users.findById(userId).orElse(null);
        if (user == null) {
            return null;
        }
        AdminProfile adminProfile = user.getRole() == Role.ADMIN
                ? adminProfiles.findById(userId).orElse(null)
                : null;

        // vendorId and creatorId stay null until those modules' migrations add
        // their profile tables; ownership checks (FR-AUTH-03) start using them then.
        return new AuthUser(
                user.getId(),
                user.getRole(),
                adminProfile == null ? null : adminProfile.getAdminRole(),
                user.getStatus(),
                user.getSessionsInvalidatedAt(),
                null,
                null);
    }
}
