package lk.ceylonpick.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lk.ceylonpick.auth.AuthProperties;
import lk.ceylonpick.auth.domain.AppUser;
import lk.ceylonpick.auth.domain.AuditLogEntry;
import lk.ceylonpick.auth.domain.RefreshToken;
import lk.ceylonpick.auth.repo.AppUserRepository;
import lk.ceylonpick.auth.repo.RefreshTokenRepository;
import lk.ceylonpick.auth.web.AuthException;
import lk.ceylonpick.shared.Hashes;
import lk.ceylonpick.shared.Ids;

/**
 * Mints and rotates sessions.
 *
 * <p>The access token carries only {@code sub} (the user id) plus the standard
 * registered claims. No role, no status: those are read live on each request,
 * so a change to either takes effect immediately rather than whenever the token
 * happens to expire. {@code iat} does double duty — a token issued before the
 * user's {@code sessions_invalidated_at} is rejected, which gives logout-
 * everywhere without adding a claim.
 *
 * <p>Refresh tokens are opaque, stored only as a hash, and single-use. Each use
 * revokes the presented row and issues a successor in the same family;
 * presenting an already-revoked row means it was captured, so the family is
 * revoked and the session ends.
 */
@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    /** 256 bits of entropy in the opaque refresh token. */
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final RefreshTokenRepository refreshTokens;
    private final SessionRevoker revoker;
    private final AppUserRepository users;
    private final AuditService audit;
    private final AuthProperties properties;
    private final Clock clock;

    public TokenService(JwtEncoder encoder,
                        JwtDecoder decoder,
                        RefreshTokenRepository refreshTokens,
                        SessionRevoker revoker,
                        AppUserRepository users,
                        AuditService audit,
                        AuthProperties properties,
                        Clock clock) {
        this.encoder = encoder;
        this.decoder = decoder;
        this.refreshTokens = refreshTokens;
        this.revoker = revoker;
        this.users = users;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- issue

    /** Starts a new session: a new refresh family plus the first access token. */
    @Transactional
    public IssuedSession startSession(AppUser user, String userAgent, String ip) {
        return issue(user, Ids.newId(), userAgent, ip);
    }

    private IssuedSession issue(AppUser user, String familyId, String userAgent, String ip) {
        Instant now = clock.instant();
        Duration accessTtl = properties.jwt().accessTtl();
        Duration refreshTtl = properties.refreshTtl().forRole(user.getRole());

        String rawRefresh = Hashes.randomToken(REFRESH_TOKEN_BYTES);
        RefreshToken row = new RefreshToken();
        row.setId(Ids.newId());
        row.setUserId(user.getId());
        row.setTokenHash(Hashes.sha256(rawRefresh));
        row.setFamilyId(familyId);
        row.setIssuedAt(now);
        row.setExpiresAt(now.plus(refreshTtl));
        row.setUserAgent(truncate(userAgent, 512));
        row.setIpHash(Hashes.ipHash(ip));
        refreshTokens.save(row);

        return new IssuedSession(accessToken(user.getId(), now, accessTtl), accessTtl, rawRefresh, refreshTtl);
    }

    private String accessToken(String userId, Instant now, Duration ttl) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .subject(userId)
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .id(Ids.newId())
                .build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    // ---------------------------------------------------------------- verify

    /** Verifies signature, expiry and issuer. Returns null for anything invalid. */
    public Jwt decodeAccessToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return decoder.decode(token);
        } catch (JwtException e) {
            log.debug("Rejected access token: {}", e.getMessage());
            return null;
        }
    }

    // ---------------------------------------------------------------- rotate

    /**
     * Exchanges a refresh token for a new pair. The presented token is always
     * consumed, whether or not the exchange succeeds.
     */
    @Transactional
    public IssuedSession rotate(String rawRefresh, String userAgent, String ip) {
        if (rawRefresh == null || rawRefresh.isBlank()) {
            throw AuthException.unauthorized("NO_SESSION", "No session to refresh");
        }
        Instant now = clock.instant();
        RefreshToken presented = refreshTokens.findByTokenHash(Hashes.sha256(rawRefresh))
                .orElseThrow(() -> AuthException.unauthorized("INVALID_SESSION", "Session is not valid"));

        if (presented.isRevoked()) {
            // Already rotated or explicitly revoked, yet someone still holds it.
            // Treat the whole family as compromised. This commits separately,
            // because the exception below rolls this transaction back.
            revoker.revokeFamily(presented.getFamilyId(), RefreshToken.REASON_REUSE_DETECTED);
            audit.record(AuditLogEntry.REFRESH_REUSE_DETECTED, presented.getUserId(), null,
                    "refresh_token", presented.getId(), ip);
            log.warn("Refresh token reuse detected for user {}; family {} revoked",
                    presented.getUserId(), presented.getFamilyId());
            throw AuthException.unauthorized("SESSION_REVOKED", "Session is no longer valid");
        }
        if (presented.isExpired(now)) {
            throw AuthException.unauthorized("SESSION_EXPIRED", "Session has expired");
        }

        AppUser user = users.findById(presented.getUserId())
                .orElseThrow(() -> AuthException.unauthorized("INVALID_SESSION", "Session is not valid"));
        if (!user.getStatus().canAuthenticate()) {
            revoker.revokeAllForUser(user.getId(), RefreshToken.REASON_SESSIONS_INVALIDATED);
            throw AuthException.forbidden("ACCOUNT_UNAVAILABLE", "This account is not active");
        }

        IssuedSession next = issue(user, presented.getFamilyId(), userAgent, ip);
        presented.revoke(now, RefreshToken.REASON_ROTATED);
        refreshTokens.findByTokenHash(Hashes.sha256(next.refreshToken()))
                .ifPresent(successor -> presented.setReplacedById(successor.getId()));
        refreshTokens.save(presented);
        return next;
    }

    // ---------------------------------------------------------------- revoke

    /** Ends the session on this device only. Other devices keep working. */
    @Transactional
    public void endSession(String rawRefresh) {
        if (rawRefresh == null || rawRefresh.isBlank()) {
            return;
        }
        refreshTokens.findByTokenHash(Hashes.sha256(rawRefresh)).ifPresent(row -> {
            row.revoke(clock.instant(), RefreshToken.REASON_LOGOUT);
            refreshTokens.save(row);
        });
    }

    /**
     * Ends every session for the user and invalidates already-issued access
     * tokens by moving {@code sessions_invalidated_at} forward.
     *
     * <p>The caller is responsible for evicting the cached {@code AuthUser};
     * {@code AccountService} does that.
     */
    @Transactional
    public void invalidateAllSessions(AppUser user, String reason) {
        Instant now = clock.instant();
        refreshTokens.revokeAllForUser(user.getId(), now, reason);
        user.setSessionsInvalidatedAt(now);
        users.save(user);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
