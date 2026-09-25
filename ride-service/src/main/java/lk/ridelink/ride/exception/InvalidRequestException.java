package lk.ridelink.ride.exception;

import org.springframework.http.HttpStatus;

/**
 * The request itself is wrong: 400.
 *
 * <p>Distinct from {@link BusinessRuleException}, which is 422. The line between them is
 * whether the request could ever have been valid:</p>
 *
 * <ul>
 *   <li><strong>400 (this class)</strong> - the input is malformed in a way no state of the
 *       system would accept. A pickup equal to its destination is never a real journey.</li>
 *   <li><strong>422</strong> - the input is perfectly well formed, but a domain rule
 *       forbids it right now. Cancelling a ride is a legitimate request; it is refused
 *       only because this particular ride is already under way.</li>
 * </ul>
 *
 * <p>The distinction matters to a client: a 400 means "fix the request", a 422 means
 * "the request was fine, the situation was not".</p>
 */
public class InvalidRequestException extends ApiException {

    public InvalidRequestException(String detail) {
        super(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED, "Validation failed", detail);
    }
}
