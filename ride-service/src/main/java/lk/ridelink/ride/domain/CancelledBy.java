package lk.ridelink.ride.domain;

/**
 * Who called the ride off.
 *
 * <p>Carried on the cancellation event because, together with the status the ride was in,
 * it decides whether a cancellation fee is due.</p>
 */
public enum CancelledBy {
    PASSENGER,
    DRIVER,
    ADMIN
}
