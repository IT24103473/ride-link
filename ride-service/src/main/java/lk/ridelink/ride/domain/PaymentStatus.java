package lk.ridelink.ride.domain;

/**
 * A read-only copy of the payment state, kept current by payment events.
 *
 * <p>This is a read model, not the truth: the Fare &amp; Payment service owns payments.
 * Holding a copy means reading a ride never has to call that service, at the cost of being
 * momentarily stale - which is the usual trade of a runtime dependency for eventual
 * consistency.</p>
 */
public enum PaymentStatus {
    /** Nothing is owed yet, because the ride has not finished. */
    NOT_DUE,
    /** A charge exists and is awaiting settlement. */
    PENDING,
    PAID,
    /** Declined; the passenger may retry, so this is not terminal. */
    FAILED
}
