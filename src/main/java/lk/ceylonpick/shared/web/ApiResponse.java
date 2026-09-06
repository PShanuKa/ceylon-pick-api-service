package lk.ceylonpick.shared.web;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The envelope every endpoint returns, success or failure.
 *
 * <p>One shape means the front end has one place to check whether a call
 * worked, and one place to read the error code from — instead of inferring it
 * from the HTTP status and a different body per endpoint.
 *
 * <pre>
 * {"success": true,  "data": { ... },                          "timestamp": "..."}
 * {"success": false, "error": {"code": "...", "message": "..."}, "timestamp": "..."}
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, ApiError error, Instant timestamp) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }

    /** For endpoints whose only outcome is "it worked". */
    public static ApiResponse<Map<String, String>> message(String message) {
        return ok(Map.of("message", message));
    }

    public static <T> ApiResponse<T> failed(ApiError error) {
        return new ApiResponse<>(false, null, error, Instant.now());
    }

    public static <T> ApiResponse<T> failed(String code, String message) {
        return failed(ApiError.of(code, message));
    }
}
