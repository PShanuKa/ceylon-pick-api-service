package lk.ceylonpick.auth.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lk.ceylonpick.auth.api.AuthUser;
import lk.ceylonpick.auth.service.AuthCookies;
import lk.ceylonpick.auth.service.AuthService;
import lk.ceylonpick.auth.service.IssuedSession;
import lk.ceylonpick.auth.service.LoginOutcome;
import lk.ceylonpick.auth.service.OtpService;
import lk.ceylonpick.auth.service.PasswordService;
import lk.ceylonpick.auth.service.RegistrationService;

/**
 * The auth surface. The SRS specifies no REST paths at all — the only fixed
 * endpoint in the whole architecture is {@code POST /webhooks/payhere} — so
 * this establishes the {@code /api/v1} convention for the rest of the service.
 *
 * <p>Statuses: 200 signed in, 202 second factor required, 401 not signed in,
 * 403 signed in but not entitled (FR-AUTH-03), 423 locked out (FR-AUTH-02),
 * 429 rate limited (FR-NOT-01).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService auth;
    private final RegistrationService registration;
    private final PasswordService passwords;
    private final OtpService otp;
    private final AuthCookies cookies;

    public AuthController(AuthService auth,
                          RegistrationService registration,
                          PasswordService passwords,
                          OtpService otp,
                          AuthCookies cookies) {
        this.auth = auth;
        this.registration = registration;
        this.passwords = passwords;
        this.otp = otp;
        this.cookies = cookies;
    }

    // ------------------------------------------------------------ password

    /** FR-AUTH-02. Returns 202 for admins and anyone with 2FA on. */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody AuthRequests.Login body,
                                   HttpServletRequest request) {
        LoginOutcome outcome = auth.loginWithPassword(body.email(), body.password(),
                ClientInfo.userAgent(request), ClientInfo.ip(request));
        return respond(outcome);
    }

    @PostMapping("/login/otp")
    public ResponseEntity<?> completeOtpLogin(@Valid @RequestBody AuthRequests.OtpLogin body,
                                              HttpServletRequest request) {
        LoginOutcome outcome = auth.completeOtpLogin(body.challengeId(), body.code(),
                ClientInfo.userAgent(request), ClientInfo.ip(request));
        return respond(outcome);
    }

    // ------------------------------------------------------------ phone OTP

    /** FR-AUTH-01 step one. Always 202, whether or not the number is known. */
    @PostMapping("/otp/request")
    public ResponseEntity<AuthResponses.OtpRequired> requestOtp(
            @Valid @RequestBody AuthRequests.PhoneOnly body) {
        LoginOutcome.OtpRequired issued = auth.requestBuyerOtp(body.phone());
        return ResponseEntity.accepted().body(toResponse(issued));
    }

    /** FR-AUTH-01 step two: creates the 30-day buyer session. */
    @PostMapping("/otp/verify")
    public ResponseEntity<AuthResponses.Session> verifyOtp(
            @Valid @RequestBody AuthRequests.OtpVerify body, HttpServletRequest request) {
        LoginOutcome.SessionIssued issued = auth.verifyBuyerOtp(body.challengeId(), body.code(),
                ClientInfo.userAgent(request), ClientInfo.ip(request));
        return withSession(issued);
    }

    /** BR-07: at most 3 resends per request, and still inside the hourly cap. */
    @PostMapping("/otp/resend")
    public ResponseEntity<AuthResponses.Message> resendOtp(
            @Valid @RequestBody AuthRequests.ChallengeOnly body) {
        otp.resend(body.challengeId());
        return ResponseEntity.accepted().body(new AuthResponses.Message("A new code is on its way"));
    }

    // ------------------------------------------------------------ registration

    /** Optional customer account. Guest checkout and phone-OTP sign-in are unaffected. */
    @PostMapping("/register")
    public ResponseEntity<AuthResponses.Message> register(
            @Valid @RequestBody AuthRequests.Register body, HttpServletRequest request) {
        registration.registerCustomer(body.email(), body.password(), body.phone(),
                body.fullName(), ClientInfo.ip(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthResponses.Message("Account created. Check your email to confirm it."));
    }

    @PostMapping("/email/verify")
    public ResponseEntity<AuthResponses.Message> verifyEmail(
            @Valid @RequestBody AuthRequests.TokenOnly body, HttpServletRequest request) {
        registration.verifyEmail(body.token(), ClientInfo.ip(request));
        return ResponseEntity.ok(new AuthResponses.Message("Email confirmed"));
    }

    // ------------------------------------------------------------ passwords

    /** Always 202, so this cannot be used to discover who has an account. */
    @PostMapping("/password/forgot")
    public ResponseEntity<AuthResponses.Message> forgotPassword(
            @Valid @RequestBody AuthRequests.ForgotPassword body, HttpServletRequest request) {
        passwords.requestReset(body.email(), ClientInfo.ip(request));
        return ResponseEntity.accepted()
                .body(new AuthResponses.Message("If that address has an account, a reset link is on its way"));
    }

    @PostMapping("/password/reset")
    public ResponseEntity<AuthResponses.Message> resetPassword(
            @Valid @RequestBody AuthRequests.ResetPassword body, HttpServletRequest request) {
        passwords.reset(body.token(), body.newPassword(), ClientInfo.ip(request));
        return clearedSession("Password changed. Sign in again.");
    }

    /** Signs the caller out everywhere, including this session. */
    @PostMapping("/password/change")
    public ResponseEntity<AuthResponses.Message> changePassword(
            @AuthenticationPrincipal AuthUser principal,
            @Valid @RequestBody AuthRequests.ChangePassword body,
            HttpServletRequest request) {
        passwords.change(principal.userId(), body.currentPassword(), body.newPassword(),
                ClientInfo.ip(request));
        return clearedSession("Password changed. Sign in again.");
    }

    // ------------------------------------------------------------ step-up (FR-AUTH-04)

    @PostMapping("/otp/step-up")
    public ResponseEntity<AuthResponses.OtpRequired> requestStepUp(
            @AuthenticationPrincipal AuthUser principal) {
        return ResponseEntity.accepted().body(toResponse(auth.requestStepUp(principal.userId())));
    }

    @PostMapping("/otp/step-up/verify")
    public ResponseEntity<AuthResponses.Message> confirmStepUp(
            @AuthenticationPrincipal AuthUser principal,
            @Valid @RequestBody AuthRequests.OtpVerify body) {
        auth.confirmStepUp(body.challengeId(), body.code(), principal.userId());
        return ResponseEntity.ok(new AuthResponses.Message("Verified"));
    }

    // ------------------------------------------------------------ session

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponses.Message> refresh(HttpServletRequest request) {
        IssuedSession session = auth.refresh(cookies.readRefresh(request),
                ClientInfo.userAgent(request), ClientInfo.ip(request));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.access(session.accessToken(), session.accessTtl()).toString())
                .header(HttpHeaders.SET_COOKIE, cookies.refresh(session.refreshToken(), session.refreshTtl()).toString())
                .body(new AuthResponses.Message("Session refreshed"));
    }

    /** Ends this device's session only. Other devices keep theirs. */
    @PostMapping("/logout")
    public ResponseEntity<AuthResponses.Message> logout(@AuthenticationPrincipal AuthUser principal,
                                                        HttpServletRequest request) {
        auth.logout(cookies.readRefresh(request),
                principal == null ? null : principal.userId(), ClientInfo.ip(request));
        return clearedSession("Signed out");
    }

    @GetMapping("/me")
    public AuthResponses.Me me(@AuthenticationPrincipal AuthUser principal) {
        return new AuthResponses.Me(principal.userId(), principal.role(), principal.adminRole(),
                principal.status(), principal.vendorId(), principal.creatorId());
    }

    // ------------------------------------------------------------ helpers

    private ResponseEntity<?> respond(LoginOutcome outcome) {
        return switch (outcome) {
            case LoginOutcome.SessionIssued issued -> withSession(issued);
            case LoginOutcome.OtpRequired pending -> ResponseEntity.accepted().body(toResponse(pending));
        };
    }

    private ResponseEntity<AuthResponses.Session> withSession(LoginOutcome.SessionIssued issued) {
        IssuedSession session = issued.session();
        ResponseCookie access = cookies.access(session.accessToken(), session.accessTtl());
        ResponseCookie refresh = cookies.refresh(session.refreshToken(), session.refreshTtl());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, access.toString())
                .header(HttpHeaders.SET_COOKIE, refresh.toString())
                .body(new AuthResponses.Session(issued.user().getId(), issued.user().getRole()));
    }

    private ResponseEntity<AuthResponses.Message> clearedSession(String message) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.clearAccess().toString())
                .header(HttpHeaders.SET_COOKIE, cookies.clearRefresh().toString())
                .body(new AuthResponses.Message(message));
    }

    private static AuthResponses.OtpRequired toResponse(LoginOutcome.OtpRequired pending) {
        return AuthResponses.OtpRequired.of(pending.challengeId(), pending.maskedPhone(), pending.devCode());
    }
}
