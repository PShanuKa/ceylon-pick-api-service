package lk.ceylonpick.auth.web;

import java.util.Map;

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
import lk.ceylonpick.shared.i18n.MessageResolver;
import lk.ceylonpick.shared.web.ApiResponse;
import lk.ceylonpick.shared.web.ClientInfo;

/**
 * The auth surface. The SRS specifies no REST paths at all — the only fixed
 * endpoint in the whole architecture is {@code POST /webhooks/payhere} — so
 * this establishes the {@code /api/v1} convention for the rest of the service.
 *
 * <p>Statuses: 200 signed in, 202 second factor required, 401 not signed in,
 * 403 signed in but not entitled (FR-AUTH-03), 423 locked out (FR-AUTH-02),
 * 429 rate limited (FR-NOT-01). Every body is an
 * {@link ApiResponse}, and every message is resolved in the caller's language.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService auth;
    private final RegistrationService registration;
    private final PasswordService passwords;
    private final OtpService otp;
    private final AuthCookies cookies;
    private final MessageResolver messages;

    public AuthController(AuthService auth,
                          RegistrationService registration,
                          PasswordService passwords,
                          OtpService otp,
                          AuthCookies cookies,
                          MessageResolver messages) {
        this.auth = auth;
        this.registration = registration;
        this.passwords = passwords;
        this.otp = otp;
        this.cookies = cookies;
        this.messages = messages;
    }

    // ------------------------------------------------------------ password

    /** FR-AUTH-02. Returns 202 for admins and anyone with 2FA on. */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<?>> login(@Valid @RequestBody AuthRequests.Login body,
                                                HttpServletRequest request) {
        return respond(auth.loginWithPassword(body.email(), body.password(),
                ClientInfo.userAgent(request), ClientInfo.ip(request)));
    }

    @PostMapping("/login/otp")
    public ResponseEntity<ApiResponse<?>> completeOtpLogin(@Valid @RequestBody AuthRequests.OtpLogin body,
                                                           HttpServletRequest request) {
        return respond(auth.completeOtpLogin(body.challengeId(), body.code(),
                ClientInfo.userAgent(request), ClientInfo.ip(request)));
    }

    // ------------------------------------------------------------ phone OTP

    /** FR-AUTH-01 step one. Always 202, whether or not the number is known. */
    @PostMapping("/otp/request")
    public ResponseEntity<ApiResponse<AuthResponses.OtpRequired>> requestOtp(
            @Valid @RequestBody AuthRequests.PhoneOnly body) {
        return ResponseEntity.accepted().body(ApiResponse.ok(toPayload(auth.requestBuyerOtp(body.phone()))));
    }

    /** FR-AUTH-01 step two: creates the 30-day buyer session. */
    @PostMapping("/otp/verify")
    public ResponseEntity<ApiResponse<AuthResponses.Session>> verifyOtp(
            @Valid @RequestBody AuthRequests.OtpVerify body, HttpServletRequest request) {
        return withSession(auth.verifyBuyerOtp(body.challengeId(), body.code(),
                ClientInfo.userAgent(request), ClientInfo.ip(request)));
    }

    /** BR-07: at most 3 resends per request, and still inside the hourly cap. */
    @PostMapping("/otp/resend")
    public ResponseEntity<ApiResponse<Map<String, String>>> resendOtp(
            @Valid @RequestBody AuthRequests.ChallengeOnly body) {
        otp.resend(body.challengeId());
        return ResponseEntity.accepted().body(message("message.OTP_SENT"));
    }

    // ------------------------------------------------------------ registration

    /** Optional customer account. Guest checkout and phone-OTP sign-in are unaffected. */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Map<String, String>>> register(
            @Valid @RequestBody AuthRequests.Register body, HttpServletRequest request) {
        registration.registerCustomer(body.email(), body.password(), body.phone(),
                body.fullName(), ClientInfo.ip(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(message("message.REGISTERED"));
    }

    @PostMapping("/email/verify")
    public ResponseEntity<ApiResponse<Map<String, String>>> verifyEmail(
            @Valid @RequestBody AuthRequests.TokenOnly body, HttpServletRequest request) {
        registration.verifyEmail(body.token(), ClientInfo.ip(request));
        return ResponseEntity.ok(message("message.EMAIL_CONFIRMED"));
    }

    // ------------------------------------------------------------ passwords

    /** Always 202, so this cannot be used to discover who has an account. */
    @PostMapping("/password/forgot")
    public ResponseEntity<ApiResponse<Map<String, String>>> forgotPassword(
            @Valid @RequestBody AuthRequests.ForgotPassword body, HttpServletRequest request) {
        passwords.requestReset(body.email(), ClientInfo.ip(request));
        return ResponseEntity.accepted().body(message("message.PASSWORD_RESET_SENT"));
    }

    @PostMapping("/password/reset")
    public ResponseEntity<ApiResponse<Map<String, String>>> resetPassword(
            @Valid @RequestBody AuthRequests.ResetPassword body, HttpServletRequest request) {
        passwords.reset(body.token(), body.newPassword(), ClientInfo.ip(request));
        return clearedSession("message.PASSWORD_CHANGED");
    }

    /** Signs the caller out everywhere, including this session. */
    @PostMapping("/password/change")
    public ResponseEntity<ApiResponse<Map<String, String>>> changePassword(
            @AuthenticationPrincipal AuthUser principal,
            @Valid @RequestBody AuthRequests.ChangePassword body,
            HttpServletRequest request) {
        passwords.change(principal.userId(), body.currentPassword(), body.newPassword(),
                ClientInfo.ip(request));
        return clearedSession("message.PASSWORD_CHANGED");
    }

    // ------------------------------------------------------------ step-up (FR-AUTH-04)

    @PostMapping("/otp/step-up")
    public ResponseEntity<ApiResponse<AuthResponses.OtpRequired>> requestStepUp(
            @AuthenticationPrincipal AuthUser principal) {
        return ResponseEntity.accepted()
                .body(ApiResponse.ok(toPayload(auth.requestStepUp(principal.userId()))));
    }

    @PostMapping("/otp/step-up/verify")
    public ResponseEntity<ApiResponse<Map<String, String>>> confirmStepUp(
            @AuthenticationPrincipal AuthUser principal,
            @Valid @RequestBody AuthRequests.OtpVerify body) {
        auth.confirmStepUp(body.challengeId(), body.code(), principal.userId());
        return ResponseEntity.ok(message("message.STEP_UP_VERIFIED"));
    }

    // ------------------------------------------------------------ session

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Map<String, String>>> refresh(HttpServletRequest request) {
        IssuedSession session = auth.refresh(cookies.readRefresh(request),
                ClientInfo.userAgent(request), ClientInfo.ip(request));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.access(session.accessToken(), session.accessTtl()).toString())
                .header(HttpHeaders.SET_COOKIE, cookies.refresh(session.refreshToken(), session.refreshTtl()).toString())
                .body(message("message.SESSION_REFRESHED"));
    }

    /** Ends this device's session only. Other devices keep theirs. */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Map<String, String>>> logout(
            @AuthenticationPrincipal AuthUser principal, HttpServletRequest request) {
        auth.logout(cookies.readRefresh(request),
                principal == null ? null : principal.userId(), ClientInfo.ip(request));
        return clearedSession("message.SIGNED_OUT");
    }

    @GetMapping("/me")
    public ApiResponse<AuthResponses.Me> me(@AuthenticationPrincipal AuthUser principal) {
        return ApiResponse.ok(new AuthResponses.Me(principal.userId(), principal.role(),
                principal.adminRole(), principal.status()));
    }

    // ------------------------------------------------------------ helpers

    private ResponseEntity<ApiResponse<?>> respond(LoginOutcome outcome) {
        return switch (outcome) {
            case LoginOutcome.SessionIssued issued -> {
                ResponseEntity<ApiResponse<AuthResponses.Session>> response = withSession(issued);
                yield ResponseEntity.status(response.getStatusCode())
                        .headers(response.getHeaders())
                        .body(response.getBody());
            }
            case LoginOutcome.OtpRequired pending ->
                    ResponseEntity.accepted().body(ApiResponse.ok(toPayload(pending)));
        };
    }

    private ResponseEntity<ApiResponse<AuthResponses.Session>> withSession(LoginOutcome.SessionIssued issued) {
        IssuedSession session = issued.session();
        ResponseCookie access = cookies.access(session.accessToken(), session.accessTtl());
        ResponseCookie refresh = cookies.refresh(session.refreshToken(), session.refreshTtl());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, access.toString())
                .header(HttpHeaders.SET_COOKIE, refresh.toString())
                .body(ApiResponse.ok(new AuthResponses.Session(
                        issued.user().getId(), issued.user().getRole())));
    }

    private ResponseEntity<ApiResponse<Map<String, String>>> clearedSession(String messageKey) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.clearAccess().toString())
                .header(HttpHeaders.SET_COOKIE, cookies.clearRefresh().toString())
                .body(message(messageKey));
    }

    private ApiResponse<Map<String, String>> message(String key) {
        return ApiResponse.message(messages.resolve(key));
    }

    private static AuthResponses.OtpRequired toPayload(LoginOutcome.OtpRequired pending) {
        return AuthResponses.OtpRequired.of(pending.challengeId(), pending.maskedPhone(), pending.devCode());
    }
}
