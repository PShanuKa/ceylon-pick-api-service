package lk.ceylonpick.shared.web;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The error half of {@link ApiResponse}.
 *
 * @param code   stable and machine-readable; this is what clients branch on
 * @param message already localised for the caller
 * @param fields  per-field detail for validation failures, keyed by field name
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, Map<String, String> fields) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, null);
    }

    public static ApiError of(String code, String message, Map<String, String> fields) {
        return new ApiError(code, message, fields);
    }
}
