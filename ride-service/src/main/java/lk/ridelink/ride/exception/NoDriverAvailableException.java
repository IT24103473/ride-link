package lk.ridelink.ride.exception;

import org.springframework.http.HttpStatus;

/**
 * Nobody could be assigned.
 *
 * <p>The ride deliberately stays REQUESTED rather than being cancelled, so the passenger
 * can simply try again in a moment - a driver may come online, or one on a nearby trip may
 * finish. Cancelling on their behalf would throw away a valid booking.</p>
 */
public class NoDriverAvailableException extends ApiException {

    public NoDriverAvailableException(String detail) {
        super(HttpStatus.CONFLICT, ErrorCodes.NO_DRIVER_AVAILABLE, "No driver available", detail);
    }
}
