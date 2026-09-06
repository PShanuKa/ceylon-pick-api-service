package lk.ceylonpick.auth.service;

import java.time.Clock;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lk.ceylonpick.auth.AuthProperties;
import lk.ceylonpick.auth.api.Role;
import lk.ceylonpick.auth.domain.AppUser;
import lk.ceylonpick.auth.domain.AuditLogEntry;
import lk.ceylonpick.auth.domain.BuyerProfile;
import lk.ceylonpick.auth.domain.OtpChallenge;
import lk.ceylonpick.auth.repo.AppUserRepository;
import lk.ceylonpick.auth.repo.BuyerProfileRepository;
import lk.ceylonpick.auth.web.AuthException;
import lk.ceylonpick.shared.Ids;
import lk.ceylonpick.shared.Phones;

/**
 * The sign-in flows.
 *
 * <ul>
 *   <li>Buyer, phone + OTP, no password (FR-AUTH-01).
 *   <li>Buyer, email + password — an optional upgrade for a returning customer.
 *       A deliberate extension: the SRS has buyers never holding a password.
 *   <li>Creator/vendor, email + password, optional OTP second factor (FR-AUTH-02).
 *   <li>Admin, email + password + mandatory OTP (FR-AUTH-02: "Admin login
 *       without OTP is impossible").
 * </ul>
 */
@Service
public class AuthService {

    private final AppUserRepository users;
    private final BuyerProfileRepository buyerProfiles;
    private final PasswordEncoder passwordEncoder;
    private final AccountService accounts;
    private final TokenService tokens;
    private final OtpService otp;
    private final AuditService audit;
    private final AuthProperties properties;
    private final Clock clock;

    public AuthService(AppUserRepository users,
                       BuyerProfileRepository buyerProfiles,
                       PasswordEncoder passwordEncoder,
                       AccountService accounts,
                       TokenService tokens,
                       OtpService otp,
                       AuditService audit,
                       AuthProperties properties,
                       Clock clock) {
        this.users = users;
        this.buyerProfiles = buyerProfiles;
        this.passwordEncoder = passwordEncoder;
        this.accounts = accounts;
        this.tokens = tokens;
        this.otp = otp;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    // ------------------------------------------------------ email + password

    @Transactional
    public LoginOutcome loginWithPassword(String email, String password, String userAgent, String ip) {
        String normalised = AppUser.normaliseEmail(email);
        Optional<AppUser> found = normalised == null
                ? Optional.empty()
                : users.findByEmail(normalised);

        // Same failure for an unknown account and a wrong password, so the API
        // cannot be used to test whether an address is registered.
        if (found.isEmpty() || !found.get().hasPassword()) {
            audit.record(AuditLogEntry.LOGIN_FAILED, null, null, "app_user", null, ip);
            throw AuthException.invalidCredentials();
        }
        AppUser user = found.get();
        accounts.assertCanAuthenticate(user);

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            accounts.recordFailedLogin(user.getId(), ip);
            audit.record(AuditLogEntry.LOGIN_FAILED, user.getId(), user.getRole().name(),
                    "app_user", user.getId(), ip);
            throw AuthException.invalidCredentials();
        }

        if (user.requiresOtpOnLogin()) {
            return challengeForSecondFactor(user);
        }
        return completeLogin(user, userAgent, ip);
    }

    private LoginOutcome challengeForSecondFactor(AppUser user) {
        if (user.getPhone() == null) {
            // Reachable only through bad data: an admin must have a phone or the
            // mandatory second factor cannot be delivered.
            throw AuthException.forbidden("NO_SECOND_FACTOR_PHONE",
                    "This account needs a mobile number before it can sign in. Contact an owner.");
        }
        OtpChallenge.Purpose purpose = user.getRole() == Role.ADMIN
                ? OtpChallenge.Purpose.ADMIN_LOGIN
                : OtpChallenge.Purpose.LOGIN_2FA;
        OtpService.IssuedOtp issued = otp.issue(purpose, user.getPhone(), user.getId(), null, null);
        return new LoginOutcome.OtpRequired(
                issued.challenge().getId(),
                Phones.mask(user.getPhone()),
                properties.otp().exposeCode() ? issued.plainCode() : null);
    }

    /** Second step of an admin or 2FA sign-in. */
    @Transactional
    public LoginOutcome completeOtpLogin(String challengeId, String code, String userAgent, String ip) {
        OtpChallenge.Purpose purpose = otp.peek(challengeId).getPurpose();
        if (purpose != OtpChallenge.Purpose.ADMIN_LOGIN && purpose != OtpChallenge.Purpose.LOGIN_2FA) {
            // e.g. an order-confirmation or bank-edit code replayed here.
            throw AuthException.badRequest("OTP_WRONG_PURPOSE", "This code cannot be used here");
        }
        OtpChallenge verified = otp.verify(challengeId, code, purpose);
        otp.consume(verified);

        AppUser user = users.findById(verified.getUserId())
                .orElseThrow(AuthException::invalidCredentials);
        accounts.assertCanAuthenticate(user);
        return completeLogin(user, userAgent, ip);
    }

    // ------------------------------------------------------ phone + OTP (buyer)

    /**
     * FR-AUTH-01 step one. No account is created here: an unknown number simply
     * receives a code, so the endpoint cannot be used to populate the user table
     * by walking through phone numbers.
     */
    @Transactional
    public LoginOutcome.OtpRequired requestBuyerOtp(String rawPhone) {
        String phone = Phones.normalise(rawPhone);
        if (phone == null) {
            throw AuthException.badRequest("INVALID_PHONE", "Enter a valid Sri Lankan mobile number");
        }
        AppUser existing = users.findByPhoneAndRole(phone, Role.BUYER).orElse(null);
        if (existing != null && existing.getStatus() != lk.ceylonpick.auth.api.UserStatus.ACTIVE) {
            throw AuthException.forbidden("ACCOUNT_UNAVAILABLE", "This account is not active");
        }

        OtpService.IssuedOtp issued = otp.issue(OtpChallenge.Purpose.BUYER_LOGIN, phone,
                existing == null ? null : existing.getId(), null, null);
        return new LoginOutcome.OtpRequired(
                issued.challenge().getId(),
                Phones.mask(phone),
                properties.otp().exposeCode() ? issued.plainCode() : null);
    }

    /** FR-AUTH-01 step two. The buyer identity is created on first success. */
    @Transactional
    public LoginOutcome.SessionIssued verifyBuyerOtp(String challengeId, String code,
                                                     String userAgent, String ip) {
        OtpChallenge verified = otp.verify(challengeId, code, OtpChallenge.Purpose.BUYER_LOGIN);
        otp.consume(verified);

        AppUser user = users.findByPhoneAndRole(verified.getPhone(), Role.BUYER)
                .orElseGet(() -> createBuyer(verified.getPhone()));
        accounts.assertCanAuthenticate(user);

        if (user.getPhoneVerifiedAt() == null) {
            user.setPhoneVerifiedAt(clock.instant());
            users.save(user);
        }
        return completeLogin(user, userAgent, ip);
    }

    private AppUser createBuyer(String phone) {
        var now = clock.instant();
        AppUser user = new AppUser();
        user.setId(Ids.newId());
        user.setPhone(phone);
        user.setRole(Role.BUYER);
        user.setSessionsInvalidatedAt(now);
        user.setPhoneVerifiedAt(now);
        user.setCreatedAt(now);
        users.save(user);

        BuyerProfile profile = new BuyerProfile();
        profile.setUserId(user.getId());
        profile.setCreatedAt(now);
        buyerProfiles.save(profile);

        audit.record(AuditLogEntry.USER_REGISTERED, user.getId(), Role.BUYER.name(),
                "app_user", user.getId(), null);
        return user;
    }

    // ------------------------------------------------------ step-up (FR-AUTH-04)

    /** Sends the fresh OTP a bank-detail edit requires. */
    @Transactional
    public LoginOutcome.OtpRequired requestStepUp(String userId) {
        AppUser user = users.findById(userId)
                .orElseThrow(() -> AuthException.unauthorized("INVALID_SESSION", "Session is not valid"));
        if (user.getPhone() == null) {
            throw AuthException.badRequest("NO_PHONE", "Add a mobile number before editing bank details");
        }
        OtpService.IssuedOtp issued = otp.issue(OtpChallenge.Purpose.BANK_EDIT, user.getPhone(),
                user.getId(), null, null);
        return new LoginOutcome.OtpRequired(
                issued.challenge().getId(),
                Phones.mask(user.getPhone()),
                properties.otp().exposeCode() ? issued.plainCode() : null);
    }

    @Transactional
    public void confirmStepUp(String challengeId, String code, String userId) {
        OtpChallenge verified = otp.verify(challengeId, code, OtpChallenge.Purpose.BANK_EDIT);
        if (!userId.equals(verified.getUserId())) {
            throw AuthException.forbidden("OTP_WRONG_USER", "This code belongs to another account");
        }
        // Deliberately not consumed: the edit endpoint checks for a recent
        // confirmation via OtpService.hasFreshStepUp.
    }

    // ------------------------------------------------------ session lifecycle

    @Transactional
    public IssuedSession refresh(String rawRefreshToken, String userAgent, String ip) {
        return tokens.rotate(rawRefreshToken, userAgent, ip);
    }

    @Transactional
    public void logout(String rawRefreshToken, String userId, String ip) {
        tokens.endSession(rawRefreshToken);
        audit.record(AuditLogEntry.LOGOUT, userId, null, "app_user", userId, ip);
    }

    private LoginOutcome.SessionIssued completeLogin(AppUser user, String userAgent, String ip) {
        accounts.recordSuccessfulLogin(user);
        IssuedSession session = tokens.startSession(user, userAgent, ip);
        audit.record(AuditLogEntry.LOGIN_SUCCEEDED, user.getId(), user.getRole().name(),
                "app_user", user.getId(), ip);
        return new LoginOutcome.SessionIssued(user, session);
    }
}
