package lk.ridelink.ride.exception;

import lk.ridelink.ride.domain.RideStatus;
import org.springframework.http.HttpStatus;

/**
 * An attempt to move a ride somewhere the state machine does not allow.
 *
 * <p>409 rather than 400: the request itself was well formed, it simply conflicts with the
 * ride's current state. The message names both states so the caller can see exactly what
 * was refused - for example cancelling a ride that has already completed.</p>
 */
public class InvalidRideTransitionException extends ApiException {

    public InvalidRideTransitionException(Object rideId, RideStatus from, RideStatus to) {
        super(HttpStatus.CONFLICT,
                ErrorCodes.INVALID_RIDE_TRANSITION,
                "Invalid ride status transition",
                "Cannot move ride " + rideId + " from " + from + " to " + to
                        + (from.isTerminal()
                            ? "; " + from + " is a terminal state"
                            : "; allowed from " + from + ": " + from.allowedTransitions()));
    }
}
