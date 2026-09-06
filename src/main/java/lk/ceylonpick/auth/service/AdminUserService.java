package lk.ceylonpick.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lk.ceylonpick.auth.api.AdminRole;
import lk.ceylonpick.auth.api.Role;
import lk.ceylonpick.auth.domain.AdminProfile;
import lk.ceylonpick.auth.domain.AppUser;
import lk.ceylonpick.shared.audit.AuditService;
import lk.ceylonpick.auth.repo.AdminProfileRepository;
import lk.ceylonpick.auth.repo.AppUserRepository;
import lk.ceylonpick.shared.web.ApiException;
import lk.ceylonpick.shared.Ids;
import lk.ceylonpick.shared.Phones;

/**
 * Creating and managing admin accounts. Restricted to {@link AdminRole#OWNER}
 * at the controller.
 *
 * <p>Two invariants are enforced here rather than in the schema:
 * every admin needs a mobile number, because FR-AUTH-02 makes their OTP
 * mandatory and undeliverable without one; and the last OWNER cannot be
 * demoted, or nobody can change settings or approve payouts again.
 */
@Service
public class AdminUserService {

    private final AppUserRepository users;
    private final AdminProfileRepository adminProfiles;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AuthUserService authUsers;
    private final AuditService audit;
    private final Clock clock;

    public AdminUserService(AppUserRepository users,
                            AdminProfileRepository adminProfiles,
                            PasswordEncoder passwordEncoder,
                            PasswordPolicy passwordPolicy,
                            AuthUserService authUsers,
                            AuditService audit,
                            Clock clock) {
        this.users = users;
        this.adminProfiles = adminProfiles;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.authUsers = authUsers;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public AdminProfile create(String rawEmail, String password, String rawPhone, String fullName,
                               AdminRole adminRole, String actorId, String ip) {
        String email = AppUser.normaliseEmail(rawEmail);
        String phone = Phones.normalise(rawPhone);
        if (email == null) {
            throw ApiException.badRequest("INVALID_EMAIL", "Enter a valid email address");
        }
        if (phone == null) {
            throw ApiException.badRequest("INVALID_PHONE",
                    "An admin needs a valid Sri Lankan mobile number for the mandatory OTP");
        }
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_IN_USE", "That email is already in use");
        }
        passwordPolicy.validate(password, email);

        Instant now = clock.instant();
        AppUser user = new AppUser();
        user.setId(Ids.newId());
        user.setEmail(email);
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(Role.ADMIN);
        user.setTwoFactorEnabled(true);   // redundant with requiresOtpOnLogin, but explicit in the data
        user.setSessionsInvalidatedAt(now);
        user.setCreatedAt(now);
        users.save(user);

        AdminProfile profile = new AdminProfile();
        profile.setUserId(user.getId());
        profile.setAdminRole(adminRole);
        profile.setFullName(fullName);
        profile.setCreatedBy(actorId);
        profile.setCreatedAt(now);
        adminProfiles.save(profile);

        audit.record(AuthAudit.ADMIN_CREATED, actorId, AdminRole.OWNER.name(),
                "admin_profile", user.getId(), ip,
                null, Map.of("adminRole", adminRole.name(), "email", email), null);
        return profile;
    }

    @Transactional
    public AdminProfile changeRole(String userId, AdminRole newRole, String actorId,
                                   String reason, String ip) {
        AdminProfile profile = adminProfiles.findById(userId)
                .orElseThrow(() -> ApiException.badRequest("UNKNOWN_ADMIN", "No such admin"));
        AdminRole previous = profile.getAdminRole();
        if (previous == newRole) {
            return profile;
        }
        if (previous == AdminRole.OWNER && adminProfiles.countByAdminRole(AdminRole.OWNER) <= 1) {
            throw ApiException.conflict("LAST_OWNER",
                    "This is the only owner. Promote another admin first.");
        }
        profile.setAdminRole(newRole);
        adminProfiles.save(profile);
        authUsers.evict(userId);

        audit.record(AuthAudit.ADMIN_ROLE_CHANGED, actorId, AdminRole.OWNER.name(),
                "admin_profile", userId, ip,
                Map.of("adminRole", previous.name()),
                Map.of("adminRole", newRole.name()),
                reason);
        return profile;
    }

    @Transactional(readOnly = true)
    public List<AdminProfile> list() {
        return adminProfiles.findAll();
    }
}
