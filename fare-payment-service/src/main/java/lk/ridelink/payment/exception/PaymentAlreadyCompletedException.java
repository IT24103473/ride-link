package lk.ridelink.payment.exception;

import org.springframework.http.HttpStatus;

/**
 * The payment has already succeeded.
 *
 * <p>Refused rather than treated as a no-op so that a double submission is visible to the
 * caller instead of silently appearing to charge twice.</p>
 */
public class PaymentAlreadyCompletedException extends ApiException {

    public PaymentAlreadyCompletedException(Object paymentId) {
        super(HttpStatus.CONFLICT,
                ErrorCodes.PAYMENT_ALREADY_COMPLETED,
                "Payment already completed",
                "Payment " + paymentId + " has already been paid");
    }
}
