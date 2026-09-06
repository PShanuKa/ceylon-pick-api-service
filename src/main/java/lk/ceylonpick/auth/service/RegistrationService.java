package lk.ceylonpick.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lk.ceylonpick.auth.AuthProperties;
import lk.ceylonpick.auth.api.Role;
import lk.ceylonpick.auth.domain.AppUser;
import lk.ceylonpick.auth.domain.AuditLogEntry;
import lk.ceylonpick.auth.domain.BuyerProfile;
import lk.ceylonpick.auth.domain.EmailVerificationToken;
import lk.ceylonpick.auth.repo.AppUserRepository;
import lk.ceylonpick.auth.repo.BuyerProfileRepository;
import lk.ceylonpick.auth.repo.EmailVerificationTokenRepository;
import lk.ceylonpick.shared.web.ApiException;
import lk.ceylonpick.shared.Hashes;
import lk.ceylonpick.shared.Ids;
import lk.ceylonpick.shared.Phones;

/**
 * Customer self-registration.
 *
 * <p>An extension beyond the SRS, which has buyers hold no password at all
 * ("no password field exists anywhere in buyer flows", FR-AUTH-01). Guest
 * checkout and phone-OTP sign-in are untouched; this only lets a returning
 * customer add an email and password to the identity their phone already
 * created.
 *
 * <p>That upgrade path is the reason registration looks for an existing buyer
 * on the same phone first: the partial unique index {@code ux_app_user_phone_buyer}
 * allows one buyer per number, so a second row would be rejected by the
 * database anyway.
 */
@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    private final AppUserRepository users;
    private final BuyerProfileRepository buyerProfiles;
    private final EmailVerificationTokenRepository verificationTokens;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AuthUserService authUsers;
    private final AuditService audit;
    private final AuthProperties properties;
    private final Clock clock;

    public RegistrationService(AppUserRepository users,
                               BuyerProfileRepository buyerProfiles,
                               EmailVerificationTokenRepository verificationTokens,
                               PasswordEncoder passwordEncoder,
                               PasswordPolicy passwordPolicy,
                               AuthUserService authUsers,
                               AuditService audit,
                               AuthProperties properties,
                               Clock clock) {
        this.users = users;
        this.buyerProfiles = buyerProfiles;
        this.verificationTokens = verificationTokens;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.authUsers = authUsers;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public AppUser registerCustomer(String rawEmail, String password, String rawPhone,
                                    String fullName, String ip) {
        String email = AppUser.normaliseEmail(rawEmail);
        String phone = Phones.normalise(rawPhone);
        if (email == null) {
            throw ApiException.badRequest("INVALID_EMAIL", "Enter a valid email address");
        }
        if (phone == null) {
            throw ApiException.badRequest("INVALID_PHONE", "Enter a valid Sri Lankan mobile number");
        }
        passwordPolicy.validate(password, email);

        Instant now = clock.instant();
        Optional<AppUser> byPhone = users.findByPhoneAndRole(phone, Role.BUYER);

        AppUser user;
        if (byPhone.isPresent()) {
            user = byPhone.get();
            if (user.hasPassword()) {
                // Already an account. Say nothing about it beyond "in use".
                throw ApiException.conflict("ALREADY_REGISTERED",
                        "An account already exists for these details");
            }
            if (users.existsByEmail(email)) {
                throw ApiException.conflict("EMAIL_IN_USE", "That email is already in use");
            }
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode(password));
        } else {
            if (users.existsByEmail(email)) {
                throw ApiException.conflict("EMAIL_IN_USE", "That email is already in use");
            }
            user = new AppUser();
            user.setId(Ids.newId());
            user.setRole(Role.BUYER);
            user.setPhone(phone);
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setSessionsInvalidatedAt(now);
            user.setCreatedAt(now);
        }
        users.save(user);
        authUsers.evict(user.getId());

        buyerProfiles.findById(user.getId()).orElseGet(() -> {
            BuyerProfile profile = new BuyerProfile();
            profile.setUserId(user.getId());
            profile.setFullName(fullName);
            profile.setCreatedAt(now);
            return buyerProfiles.save(profile);
        });

        issueEmailVerification(user, now);
        audit.record(AuditLogEntry.USER_REGISTERED, user.getId(), Role.BUYER.name(),
                "app_user", user.getId(), ip);
        return user;
    }

    private void issueEmailVerification(AppUser user, Instant now) {
        String raw = Hashes.randomToken(32);
        EmailVerificationToken token = new EmailVerificationToken();
        token.setId(Ids.newId());
        token.setUserId(user.getId());
        token.setEmail(user.getEmail());
        token.setTokenHash(Hashes.sha256(raw));
        token.setExpiresAt(now.plus(properties.reset().emailTokenTtl()));
        token.setCreatedAt(now);
        verificationTokens.save(token);

        // Replace with the notifications module once it exists (Architecture §4).
        log.warn("No mailer configured. Email verification token for {} is {}", user.getEmail(), raw);
    }

    @Transactional
    public void verifyEmail(String rawToken, String ip) {
        Instant now = clock.instant();
        EmailVerificationToken token = verificationTokens.findByTokenHash(Hashes.sha256(rawToken))
                .orElseThrow(() -> ApiException.badRequest("INVALID_TOKEN", "That link is not valid"));
        if (!token.isUsable(now)) {
            throw ApiException.badRequest("INVALID_TOKEN", "That link has expired or was already used");
        }
        AppUser user = users.findById(token.getUserId())
                .orElseThrow(() -> ApiException.badRequest("INVALID_TOKEN", "That link is not valid"));

        user.setEmailVerifiedAt(now);
        users.save(user);
        token.setUsedAt(now);
        verificationTokens.save(token);
        authUsers.evict(user.getId());

        audit.record(AuditLogEntry.EMAIL_VERIFIED, user.getId(), user.getRole().name(),
                "app_user", user.getId(), ip);
    }
}
