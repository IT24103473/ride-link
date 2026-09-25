package lk.ridelink.payment.domain;

/**
 * Lifecycle of a payment.
 *
 * <p>{@link #FAILED} is not terminal: a declined card can be retried, and each attempt is
 * recorded separately. {@link #SUCCEEDED} is terminal - a paid ride cannot be paid
 * again.</p>
 */
public enum PaymentStatus {
    PENDING,
    FAILED,
    SUCCEEDED
}
