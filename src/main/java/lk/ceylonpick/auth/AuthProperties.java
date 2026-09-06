package lk.ceylonpick.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import lk.ceylonpick.auth.api.Role;

/** Everything under {@code ceylonpick.auth} in the profile yaml. */
@ConfigurationProperties(prefix = "ceylonpick.auth")
public record AuthProperties(
        Jwt jwt,
        Cookie cookie,
        RefreshTtl refreshTtl,
        Lockout lockout,
        Password password,
        Otp otp,
        Reset reset,
        Cache cache,
        Bootstrap bootstrap) {

    public record Jwt(
            @DefaultValue("ceylonpick") String issuer,
            String secret,
            @DefaultValue("15m") Duration accessTtl) {
    }

    public record Cookie(
            @DefaultValue("cp_at") String accessName,
            @DefaultValue("cp_rt") String refreshName,
            @DefaultValue("/") String path,
            /** Narrower than {@code path}: the refresh cookie rides only on the auth endpoints. */
            @DefaultValue("/api/v1/auth") String refreshPath,
            @DefaultValue("true") boolean secure,
            @DefaultValue("Lax") String sameSite) {
    }

    /**
     * FR-AUTH-01 fixes the buyer session at 30 days. The staff values are not in
     * the SRS; admin is shortest because that session can change money.
     */
    public record RefreshTtl(
            @DefaultValue("30d") Duration buyer,
            @DefaultValue("7d") Duration creator,
            @DefaultValue("7d") Duration vendor,
            @DefaultValue("1d") Duration admin) {

        public Duration forRole(Role role) {
            return switch (role) {
                case BUYER -> buyer;
                case CREATOR -> creator;
                case VENDOR -> vendor;
                case ADMIN -> admin;
            };
        }
    }

    /** FR-AUTH-02: "5 failed attempts -> 15-min lockout". */
    public record Lockout(
            @DefaultValue("5") int maxFailedAttempts,
            @DefaultValue("15m") Duration duration) {
    }

    /** FR-AUTH-02: "password policy 10 chars". */
    public record Password(@DefaultValue("10") int minLength) {
    }

    /** BR-07 and FR-NOT-01 / IF-04. */
    public record Otp(
            @DefaultValue("6") int length,
            @DefaultValue("10m") Duration ttl,
            @DefaultValue("3") int maxAttempts,
            @DefaultValue("3") int maxResends,
            /** FR-NOT-01 / IF-04, for buyer-facing codes. */
            @DefaultValue("3") int perPhonePerHour,
            /**
             * The same cap applied to a staff sign-in would lock an admin out of
             * the platform for an hour after three logins, because FR-AUTH-02
             * makes their OTP mandatory. The spec's limit exists to bound SMS
             * cost and abuse on buyer flows, neither of which applies to a
             * handful of known staff numbers, so those get their own ceiling.
             */
            @DefaultValue("10") int perStaffPhonePerHour,
            @DefaultValue("false") boolean exposeCode) {
    }

    public record Reset(
            @DefaultValue("1h") Duration passwordTokenTtl,
            @DefaultValue("24h") Duration emailTokenTtl) {
    }

    public record Cache(
            @DefaultValue("5m") Duration ttl,
            @DefaultValue("10000") long maxSize) {
    }

    /**
     * The first OWNER. Admin accounts can only be created by an OWNER, so
     * without a seed there is no way in. Applied only when no admin exists at
     * all, which makes it a no-op on every start after the first.
     */
    public record Bootstrap(
            @DefaultValue("false") boolean enabled,
            String email,
            String password,
            String phone,
            @DefaultValue("Owner") String fullName) {
    }
}
