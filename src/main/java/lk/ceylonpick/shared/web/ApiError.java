package lk.ceylonpick.shared.web;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The error half of {@link ApiResponse}.
 *
 * @param code   stable and machine-readable; this is what clients branch on
 * @param message already localised for the caller
 * @param fields  per-field detail for validation failures, keyed by field name
 * @param details machine-readable context, e.g. the allowed targets of a
 *                refused state transition
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        String code,
        String message,
        Map<String, String> fields,
        Map<String, Object> details) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, null, null);
    }

    public static ApiError of(String code, String message, Map<String, String> fields) {
        return new ApiError(code, message, fields, null);
    }

    public static ApiError withDetails(String code, String message, Map<String, Object> details) {
        return new ApiError(code, message, null, details);
    }
}
