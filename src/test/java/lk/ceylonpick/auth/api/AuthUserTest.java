package lk.ceylonpick.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

class AuthUserTest {

    private static final Instant INVALIDATED_AT = Instant.parse("2026-09-06T10:00:00Z");

    private static AuthUser user(Role role, AdminRole adminRole) {
        return new AuthUser("u1", role, adminRole, UserStatus.ACTIVE, INVALIDATED_AT);
    }

    @Test
    void grantsTheRoleAuthority() {
        assertThat(user(Role.VENDOR, null).authorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_VENDOR");
    }

    @Test
    void grantsTheAdminTierAlongsideTheRole() {
        assertThat(user(Role.ADMIN, AdminRole.OWNER).authorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ADMIN_OWNER");
        assertThat(user(Role.ADMIN, AdminRole.MANAGER).isOwner()).isFalse();
    }

    /**
     * This is what makes logout-everywhere immediate: an access token minted
     * before the invalidation instant is refused, without needing a claim of its
     * own in the token.
     */
    @Test
    void rejectsTokensMintedBeforeSessionsWereInvalidated() {
        AuthUser subject = user(Role.BUYER, null);
        assertThat(subject.invalidatesTokenIssuedAt(INVALIDATED_AT.minus(1, ChronoUnit.SECONDS))).isTrue();
        assertThat(subject.invalidatesTokenIssuedAt(INVALIDATED_AT)).isFalse();
        assertThat(subject.invalidatesTokenIssuedAt(INVALIDATED_AT.plus(1, ChronoUnit.SECONDS))).isFalse();
    }

    /** A token with no issued-at cannot be placed relative to the cut-off, so it loses. */
    @Test
    void rejectsTokensWithNoIssuedAt() {
        assertThat(user(Role.BUYER, null).invalidatesTokenIssuedAt(null)).isTrue();
    }

    @Test
    void onlyActiveAccountsMayAuthenticate() {
        assertThat(UserStatus.ACTIVE.canAuthenticate()).isTrue();
        assertThat(UserStatus.LOCKED.canAuthenticate()).isFalse();
        assertThat(UserStatus.DISABLED.canAuthenticate()).isFalse();
    }
}
