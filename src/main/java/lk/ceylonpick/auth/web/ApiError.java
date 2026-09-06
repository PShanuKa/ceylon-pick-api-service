package lk.ceylonpick.auth.web;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The error shape for the whole API: a stable machine-readable {@code code} and
 * a message safe to show a user.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        String code,
        String message,
        /** Field-level detail for validation failures. */
        Map<String, String> fields,
        Instant timestamp) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, null, Instant.now());
    }

    public static ApiError of(String code, String message, Map<String, String> fields) {
        return new ApiError(code, message, fields, Instant.now());
    }
}
