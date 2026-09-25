package lk.ridelink.payment.exception;

import java.time.Instant;
import org.springframework.http.HttpStatus;

/**
 * The quoted estimate is older than its validity window.
 *
 * <p>Estimates expire so that a passenger cannot hold an old quote indefinitely and claim
 * it after tariffs change.</p>
 */
public class FareEstimateExpiredException extends ApiException {

    public FareEstimateExpiredException(Object estimateId, Instant expiredAt) {
        super(HttpStatus.UNPROCESSABLE_ENTITY,
                ErrorCodes.FARE_ESTIMATE_EXPIRED,
                "Fare estimate expired",
                "Fare estimate " + estimateId + " expired at " + expiredAt
                        + "; request a new estimate");
    }
}
