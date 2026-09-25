package lk.ridelink.driver.domain;

/**
 * Dispatch state of a driver.
 *
 * <p>{@link #BUSY} is never settable through the API: it is entered only by a successful
 * reservation from the Ride service and left only by a release. That keeps the flag
 * honest - a driver cannot mark themselves free while still on a trip.</p>
 */
public enum Availability {
    OFFLINE,
    AVAILABLE,
    BUSY
}
