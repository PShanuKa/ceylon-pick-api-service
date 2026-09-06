package lk.ceylonpick.auth.api;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * The authorization snapshot for one person — everything needed to decide a
 * request, and nothing else.
 *
 * <p>This is what gets cached. The access token carries only the user id, so
 * this record is loaded on every authenticated request (cache, falling back to
 * the database). That is deliberate: a role change, a lock or a disable takes
 * effect on the next request rather than when the token happens to expire.
 *
 * <p>Deliberately absent: {@code password_hash}, and the {@code failed_logins}
 * and {@code locked_until} counters. Credentials do not belong in a long-lived
 * heap object, and the lockout counters must be read and written
 * transactionally or the 5-attempt limit can be raced past.
 */
public record AuthUser(
        String userId,
        Role role,
        AdminRole adminRole,
        UserStatus status,
        /** Access tokens issued before this instant are rejected — see logout-all. */
        Instant sessionsInvalidatedAt,
        /** Profile ids for ownership checks (FR-AUTH-03); null until that profile exists. */
        String vendorId,
        String creatorId) {

    public Collection<GrantedAuthority> authorities() {
        List<GrantedAuthority> list = new ArrayList<>(2);
        list.add(new SimpleGrantedAuthority(role.authority()));
        if (adminRole != null) {
            list.add(new SimpleGrantedAuthority(adminRole.authority()));
        }
        return list;
    }

    public boolean isOwner() {
        return adminRole == AdminRole.OWNER;
    }

    /** True if a token minted at {@code issuedAt} predates the last session invalidation. */
    public boolean invalidatesTokenIssuedAt(Instant issuedAt) {
        return issuedAt == null || issuedAt.isBefore(sessionsInvalidatedAt);
    }
}
