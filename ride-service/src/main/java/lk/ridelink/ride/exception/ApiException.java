package lk.ridelink.ride.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for every error this service raises deliberately.
 *
 * <p>Carrying the status and the error code on the exception itself means
 * {@link GlobalExceptionHandler} needs one handler for all of them rather than one
 * {@code @ExceptionHandler} method per exception type - each new business error is a
 * new subclass and no change to the handler.</p>
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String title;

    protected ApiException(HttpStatus status, String code, String title, String detail) {
        super(detail);
        this.status = status;
        this.code = code;
        this.title = title;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }
}
