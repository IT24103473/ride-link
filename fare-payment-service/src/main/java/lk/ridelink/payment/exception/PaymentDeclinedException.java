package lk.ridelink.payment.exception;

import org.springframework.http.HttpStatus;

/**
 * The simulated card was declined.
 *
 * <p>402 rather than 400: the request was well formed and was processed - the payment
 * simply did not succeed. The payment stays in FAILED and can be retried with a different
 * token, which the Postman collection demonstrates.</p>
 */
public class PaymentDeclinedException extends ApiException {

    private final String failureReason;

    public PaymentDeclinedException(String failureReason) {
        super(HttpStatus.PAYMENT_REQUIRED,
                ErrorCodes.PAYMENT_DECLINED,
                "Payment declined",
                "The payment was declined: " + failureReason + ". You may retry.");
        this.failureReason = failureReason;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
