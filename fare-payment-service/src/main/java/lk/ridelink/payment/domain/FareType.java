package lk.ridelink.payment.domain;

/**
 * What a charge is for.
 *
 * <p>Part of the unique key on payment, so a ride can carry at most one trip fare and at
 * most one cancellation fee - never two of either, however many times an event is
 * redelivered.</p>
 */
public enum FareType {
    /** The completed journey. */
    TRIP,
    /** Flat fee when a passenger cancels after a driver had accepted. */
    CANCELLATION_FEE
}
