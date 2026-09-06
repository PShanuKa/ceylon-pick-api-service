package lk.ceylonpick.auth.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import lk.ceylonpick.auth.api.AdminRole;
import lk.ceylonpick.auth.api.Role;
import lk.ceylonpick.auth.api.UserStatus;

/**
 * The {@code data} payloads of this module's responses; the envelope around
 * them is {@link lk.ceylonpick.shared.web.ApiResponse}.
 *
 * <p>No token ever appears in a body — the session lives in httpOnly cookies
 * (NFR-05), so there is nothing here for injected script to read.
 */
public final class AuthResponses {

    private AuthResponses() {
    }

    /** A completed sign-in. Call {@code /me} for the full picture. */
    public record Session(String userId, Role role) {
    }

    /**
     * A second factor is still needed. {@code devCode} is present only while
     * {@code ceylonpick.auth.otp.expose-code} is on, which is dev only.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OtpRequired(
            String status,
            String challengeId,
            String sentTo,
            String devCode) {

        public static OtpRequired of(String challengeId, String maskedPhone, String devCode) {
            return new OtpRequired("OTP_REQUIRED", challengeId, maskedPhone, devCode);
        }
    }

    /** {@code GET /api/v1/auth/me}. Everything here is read live, never from the token. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Me(
            String userId,
            Role role,
            AdminRole adminRole,
            UserStatus status,
            String vendorId,
            String creatorId) {
    }

    public record AdminSummary(String userId, String fullName, AdminRole adminRole) {
    }
}
