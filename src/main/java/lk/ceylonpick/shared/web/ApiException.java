package lk.ceylonpick.shared.web;

import org.springframework.http.HttpStatus;

/**
 * A failure that maps directly onto a response.
 *
 * <p>The {@code code} is the contract: it is stable, machine-readable, and the
 * key the front end switches on. The message is a readable fallback — the
 * handler prefers a translation of {@code error.<code>} when one exists
 * (see {@link lk.ceylonpick.shared.i18n.MessageResolver}), so adding SI/TA
 * copy never means touching a throw site.
 *
 * <p>Messages on the sign-in paths are deliberately vague: a wrong email, a
 * wrong password and an unknown account all produce the same
 * {@code INVALID_CREDENTIALS}, so the API cannot be used to discover who has an
 * account.
 */
public class ApiException extends RuntimeException {

    private static final Object[] NO_ARGS = {};

    private final HttpStatus status;
    private final String code;
    /** Substituted into the translated message, so numbers survive translation. */
    private final transient Object[] args;

    public ApiException(HttpStatus status, String code, String message, Object... args) {
        super(message);
        this.status = status;
        this.code = code;
        this.args = args == null ? NO_ARGS : args;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public Object[] getArgs() {
        return args.clone();
    }

    public static ApiException badRequest(String code, String message, Object... args) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message, args);
    }

    public static ApiException unauthorized(String code, String message, Object... args) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, message, args);
    }

    /** Authenticated but not entitled — never used for "not signed in" (FR-AUTH-03). */
    public static ApiException forbidden(String code, String message, Object... args) {
        return new ApiException(HttpStatus.FORBIDDEN, code, message, args);
    }

    public static ApiException notFound(String code, String message, Object... args) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message, args);
    }

    public static ApiException conflict(String code, String message, Object... args) {
        return new ApiException(HttpStatus.CONFLICT, code, message, args);
    }

    /** 423, so a client can show a countdown rather than a generic refusal. */
    public static ApiException locked(String code, String message, Object... args) {
        return new ApiException(HttpStatus.LOCKED, code, message, args);
    }

    public static ApiException tooManyRequests(String code, String message, Object... args) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, code, message, args);
    }
}
