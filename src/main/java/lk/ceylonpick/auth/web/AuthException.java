package lk.ceylonpick.auth.web;

import org.springframework.http.HttpStatus;

/**
 * A failure that maps directly onto a response.
 *
 * <p>The messages are deliberately vague on the sign-in paths: a wrong email, a
 * wrong password and an unknown account all produce the same
 * {@code INVALID_CREDENTIALS}, so the API cannot be used to discover who has an
 * account.
 */
public class AuthException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public AuthException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public static AuthException invalidCredentials() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                "Email or password is incorrect");
    }

    public static AuthException unauthorized(String code, String message) {
        return new AuthException(HttpStatus.UNAUTHORIZED, code, message);
    }

    public static AuthException forbidden(String code, String message) {
        return new AuthException(HttpStatus.FORBIDDEN, code, message);
    }

    /** 423, used for the FR-AUTH-02 failed-login lockout so the client can show a countdown. */
    public static AuthException locked(String message) {
        return new AuthException(HttpStatus.LOCKED, "ACCOUNT_LOCKED", message);
    }

    public static AuthException badRequest(String code, String message) {
        return new AuthException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static AuthException conflict(String code, String message) {
        return new AuthException(HttpStatus.CONFLICT, code, message);
    }

    /** 429, used for the FR-NOT-01 cap of 3 OTPs per phone per hour. */
    public static AuthException tooManyRequests(String code, String message) {
        return new AuthException(HttpStatus.TOO_MANY_REQUESTS, code, message);
    }
}
