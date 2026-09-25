package lk.ridelink.ride.exception;

import org.springframework.http.HttpStatus;

/**
 * The passenger already has a ride in progress.
 *
 * <p>One active ride at a time keeps the model honest: a passenger can only physically be
 * in one vehicle, and allowing several would make driver reservations impossible to
 * reason about.</p>
 */
public class ActiveRideExistsException extends ApiException {

    public ActiveRideExistsException(Object rideId, Object status) {
        super(HttpStatus.CONFLICT,
                ErrorCodes.ACTIVE_RIDE_EXISTS,
                "Active ride already exists",
                "You already have an active ride (" + rideId + ", currently " + status
                        + "). Complete or cancel it before requesting another.");
    }
}
