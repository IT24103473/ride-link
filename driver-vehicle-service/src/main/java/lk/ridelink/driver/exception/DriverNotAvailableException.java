package lk.ridelink.driver.exception;

import org.springframework.http.HttpStatus;

/**
 * A reservation attempt lost: the driver is no longer AVAILABLE.
 *
 * <p>This is an expected outcome, not a fault. When two passengers request at the same
 * moment, exactly one reservation must fail, and the Ride service responds by trying the
 * next ranked candidate.</p>
 */
public class DriverNotAvailableException extends ApiException {

    public DriverNotAvailableException(Object driverId) {
        super(HttpStatus.CONFLICT,
                ErrorCodes.DRIVER_NOT_AVAILABLE,
                "Driver not available",
                "Driver " + driverId + " is not AVAILABLE and cannot be reserved");
    }
}
