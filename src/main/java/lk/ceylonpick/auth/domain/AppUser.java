package lk.ceylonpick.auth.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lk.ceylonpick.auth.api.Role;
import lk.ceylonpick.auth.api.UserStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row per person, whatever roles they hold. See {@code V1__auth.sql} for
 * the credential shapes this must legally hold.
 */
@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
public class AppUser {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    /** Always stored lower-cased; {@link #normaliseEmail} is the only way it is set. */
    @Column(name = "email")
    private String email;

    @Column(name = "phone")
    private String phone;

    @Column(name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatus status = UserStatus.ACTIVE;

    /** FR-AUTH-02 optional second factor. Forced on for {@link Role#ADMIN}. */
    @Column(name = "two_factor_enabled", nullable = false)
    private boolean twoFactorEnabled;

    @Column(name = "failed_logins", nullable = false)
    private int failedLogins;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "phone_verified_at")
    private Instant phoneVerifiedAt;

    @Column(name = "sessions_invalidated_at", nullable = false)
    private Instant sessionsInvalidatedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "language", nullable = false)
    private String language = "en";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Maintained by the {@code trg_touch_app_user} trigger, never by JPA. */
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    public static String normaliseEmail(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim().toLowerCase();
    }

    public void setEmail(String email) {
        this.email = normaliseEmail(email);
    }

    /** True while the failed-login lockout from FR-AUTH-02 is still in force. */
    public boolean isTemporarilyLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public boolean hasPassword() {
        return passwordHash != null;
    }

    /** Admins always need OTP (FR-AUTH-02: "Admin login without OTP is impossible"). */
    public boolean requiresOtpOnLogin() {
        return role == Role.ADMIN || twoFactorEnabled;
    }
}
