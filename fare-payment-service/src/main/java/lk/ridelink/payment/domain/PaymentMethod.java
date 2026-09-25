package lk.ridelink.payment.domain;

/**
 * How a ride is paid for.
 *
 * <p>Both are simulated. CASH always succeeds, since the money changes hands in the
 * vehicle and this service is only recording it; CARD runs the fake token flow.</p>
 */
public enum PaymentMethod {
    CASH,
    CARD
}
