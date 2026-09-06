package lk.ceylonpick.auth.service;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lk.ceylonpick.auth.repo.RefreshTokenRepository;

/**
 * Revokes refresh tokens in a transaction of its own.
 *
 * <p>Separate from {@link TokenService} for two reasons. Reuse detection ends by
 * throwing, which rolls the caller back — a revocation written in that
 * transaction would vanish with the exception, leaving the stolen family alive.
 * And a self-call inside {@code TokenService} would bypass the proxy, so the new
 * propagation would never take effect.
 */
@Service
public class SessionRevoker {

    private final RefreshTokenRepository refreshTokens;
    private final Clock clock;

    public SessionRevoker(RefreshTokenRepository refreshTokens, Clock clock) {
        this.refreshTokens = refreshTokens;
        this.clock = clock;
    }

    /** Kills every live token in the family, committed independently of the caller. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int revokeFamily(String familyId, String reason) {
        return refreshTokens.revokeFamily(familyId, clock.instant(), reason);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int revokeAllForUser(String userId, String reason) {
        return refreshTokens.revokeAllForUser(userId, clock.instant(), reason);
    }
}
