package lk.ridelink.payment.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import lk.ridelink.payment.config.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every exception into the same RFC 7807 {@link ProblemDetail} body.
 *
 * <p>All four services return this identical shape, so a client can parse one error
 * format regardless of which service answered:</p>
 *
 * <pre>
 * { "type", "title", "status", "detail",
 *   "code", "timestamp", "path", "correlationId", "errors"? }
 * </pre>
 *
 * <p>Extending {@link ResponseEntityExceptionHandler} means Spring's own failures
 * (unreadable body, validation, unsupported method) are routed through here too,
 * instead of falling back to the default whitelabel body.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Every deliberate business error. One handler covers them all because
     * {@link ApiException} carries its own status and code.
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException ex, HttpServletRequest request) {
        log.debug("Business error {} on {}: {}", ex.getCode(), request.getRequestURI(), ex.getMessage());
        ProblemDetail problem = build(ex.getStatus(), ex.getTitle(), ex.getMessage(),
                ex.getCode(), request.getRequestURI());
        return ResponseEntity.status(ex.getStatus()).body(problem);
    }

    /**
     * Thrown by {@code @PreAuthorize} and by ownership checks in the service layer.
     * Handled here rather than by Spring Security's default so that a 403 body looks
     * like every other error.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex,
                                                            HttpServletRequest request) {
        ProblemDetail problem = build(HttpStatus.FORBIDDEN, "Forbidden",
                "You do not have permission to perform this action",
                ErrorCodes.FORBIDDEN, request.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    /**
     * Two writers raced on the same row and the {@code @Version} check lost. Reported as
     * 409 because retrying the request is the correct client response.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException ex,
                                                              HttpServletRequest request) {
        log.warn("Optimistic lock conflict on {}", request.getRequestURI());
        ProblemDetail problem = build(HttpStatus.CONFLICT, "Concurrent modification",
                "The record was changed by someone else; retry the request",
                ErrorCodes.CONCURRENT_MODIFICATION, request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    /**
     * Anything unanticipated. The real cause is logged but never returned: a stack trace
     * or SQL fragment in the response body is an information leak.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {}", request.getRequestURI(), ex);
        ProblemDetail problem = build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "An unexpected error occurred", "INTERNAL_ERROR", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    /** Bean Validation failures on a {@code @RequestBody}, reported field by field. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        // TreeMap so the field order in the body is stable and assertable in tests.
        Map<String, String> fieldErrors = new TreeMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));

        ProblemDetail problem = build(HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more fields are invalid", ErrorCodes.VALIDATION_FAILED, path(request));
        problem.setProperty("errors", fieldErrors);
        return ResponseEntity.badRequest().body(problem);
    }

    /** Validation on path variables and request parameters. */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail problem = build(HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more request parameters are invalid",
                ErrorCodes.VALIDATION_FAILED, path(request));
        return ResponseEntity.badRequest().body(problem);
    }

    /** Malformed JSON, a wrong type, or an unparseable enum value. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        ProblemDetail problem = build(HttpStatus.BAD_REQUEST, "Malformed request",
                "The request body could not be read; check the JSON syntax and field types",
                ErrorCodes.MALFORMED_REQUEST, path(request));
        return ResponseEntity.badRequest().body(problem);
    }

    /**
     * Fallback for the remaining Spring MVC exceptions (unsupported method, missing
     * parameter, wrong media type) so they carry a {@code code} like everything else.
     */
    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body,
                                                          HttpHeaders headers,
                                                          HttpStatusCode statusCode,
                                                          WebRequest request) {
        if (body instanceof ProblemDetail problem && problem.getProperties() == null) {
            decorate(problem, ErrorCodes.MALFORMED_REQUEST, path(request));
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    /**
     * Shared body builder. Public and static so the security entry point and access
     * denied handler - which run outside this advice - produce an identical shape.
     */
    public static ProblemDetail build(HttpStatus status, String title, String detail,
                                      String code, String path) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        decorate(problem, code, path);
        return problem;
    }

    private static void decorate(ProblemDetail problem, String code, String path) {
        // LinkedHashMap keeps these in a predictable order in the JSON output.
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("code", code);
        extra.put("timestamp", Instant.now().toString());
        extra.put("path", path);
        extra.put("correlationId", CorrelationIdFilter.current());
        extra.forEach(problem::setProperty);
    }

    private static String path(WebRequest request) {
        // WebRequest exposes the path as "uri=/api/v1/...".
        String description = request.getDescription(false);
        return description.startsWith("uri=") ? description.substring(4) : description;
    }
}
