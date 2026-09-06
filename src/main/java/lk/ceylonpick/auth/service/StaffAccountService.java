package lk.ceylonpick.auth.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lk.ceylonpick.auth.api.Role;
import lk.ceylonpick.auth.api.StaffAccounts;
import lk.ceylonpick.auth.domain.AppUser;
import lk.ceylonpick.auth.repo.AppUserRepository;
import lk.ceylonpick.shared.Hashes;
import lk.ceylonpick.shared.Ids;
import lk.ceylonpick.shared.Phones;
import lk.ceylonpick.shared.audit.AuditService;
import lk.ceylonpick.shared.web.ApiException;

@Service
public class StaffAccountService implements StaffAccounts {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final PasswordService passwords;
    private final AuditService audit;
    private final Clock clock;

    public StaffAccountService(AppUserRepository users,
                               PasswordEncoder passwordEncoder,
                               PasswordService passwords,
                               AuditService audit,
                               Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.passwords = passwords;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional
    public String provision(Role role, String rawEmail, String rawPhone) {
        if (role == Role.BUYER) {
            throw ApiException.badRequest("INVALID_ROLE", "Buyers are provisioned by phone, not here");
        }
        String email = AppUser.normaliseEmail(rawEmail);
        String phone = Phones.normalise(rawPhone);
        if (email == null) {
            throw ApiException.badRequest("INVALID_EMAIL", "Enter a valid email address");
        }
        if (phone == null) {
            throw ApiException.badRequest("INVALID_PHONE", "Enter a valid Sri Lankan mobile number");
        }
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_IN_USE", "That email is already in use");
        }

        Instant now = clock.instant();
        AppUser user = new AppUser();
        user.setId(Ids.newId());
        user.setEmail(email);
        user.setPhone(phone);
        // A random, unusable value. The account is reachable only through the
        // set-password link below, so nobody — us included — ever knows a
        // password the new vendor did not choose.
        user.setPasswordHash(passwordEncoder.encode(Hashes.randomToken(32)));
        user.setRole(role);
        user.setSessionsInvalidatedAt(now);
        user.setCreatedAt(now);
        users.save(user);

        passwords.requestReset(email, null);
        audit.record(AuthAudit.USER_REGISTERED, user.getId(), role.name(), "app_user", user.getId(), null);
        return user.getId();
    }
}
