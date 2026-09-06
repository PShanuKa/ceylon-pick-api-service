package lk.ceylonpick.auth.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lk.ceylonpick.auth.api.AdminRole;

/** Request bodies for the auth endpoints. */
public final class AuthRequests {

    private AuthRequests() {
    }

    public record Login(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    /** Second step for admins (mandatory) and 2FA-enabled staff. */
    public record OtpLogin(
            @NotBlank String challengeId,
            @NotBlank @Size(min = 4, max = 10) String code) {
    }

    public record PhoneOnly(@NotBlank String phone) {
    }

    public record OtpVerify(
            @NotBlank String challengeId,
            @NotBlank @Size(min = 4, max = 10) String code) {
    }

    public record ChallengeOnly(@NotBlank String challengeId) {
    }

    /** Customer self-registration. Phone stays the identity; email is the addition. */
    public record Register(
            @NotBlank @Email String email,
            @NotBlank String password,
            @NotBlank String phone,
            String fullName) {
    }

    public record TokenOnly(@NotBlank String token) {
    }

    public record ForgotPassword(@NotBlank @Email String email) {
    }

    public record ResetPassword(
            @NotBlank String token,
            @NotBlank String newPassword) {
    }

    public record ChangePassword(
            @NotBlank String currentPassword,
            @NotBlank String newPassword) {
    }

    public record CreateAdmin(
            @NotBlank @Email String email,
            @NotBlank String password,
            @NotBlank String phone,
            @NotBlank String fullName,
            AdminRole adminRole) {
    }

    public record ChangeAdminRole(
            AdminRole adminRole,
            String reason) {
    }
}
