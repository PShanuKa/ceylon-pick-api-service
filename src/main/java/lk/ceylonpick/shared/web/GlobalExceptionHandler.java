package lk.ceylonpick.shared.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lk.ceylonpick.shared.i18n.MessageResolver;

/**
 * Turns exceptions into the one {@link ApiResponse} envelope, in the caller's
 * language.
 *
 * <p>Applies to every module, which is why it lives in the shared kernel rather
 * than beside the endpoints of whichever module happened to need it first.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MessageResolver messages;

    public GlobalExceptionHandler(MessageResolver messages) {
        this.messages = messages;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApi(ApiException e) {
        String message = messages.forErrorCode(e.getCode(), e.getMessage(), e.getArgs());
        return ResponseEntity.status(e.getStatus())
                .body(ApiResponse.failed(ApiError.of(e.getCode(), message)));
    }

    /** FR-AUTH-03: authenticated but not entitled is a 403, never a 401. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.failed(ApiError.of("FORBIDDEN",
                        messages.forErrorCode("FORBIDDEN", "You do not have access to this"))));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
                .body(ApiResponse.failed(ApiError.of("VALIDATION_FAILED",
                        messages.forErrorCode("VALIDATION_FAILED", "Check the highlighted fields"), fields)));
    }

    /**
     * The catch-all. The detail is logged, never returned: a stack trace or a
     * database message in a response body is free reconnaissance.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failed(ApiError.of("INTERNAL",
                        messages.forErrorCode("INTERNAL", "Something went wrong. Please try again."))));
    }
}
