package lk.ridelink.driver.exception;

import org.springframework.http.HttpStatus;

/**
 * A driver tried to go offline while still on a ride.
 *
 * <p>Refused because the passenger is mid-trip: the driver must complete or cancel the
 * ride first, which releases them through the normal path.</p>
 */
public class DriverBusyException extends ApiException {

    public DriverBusyException(Object rideId) {
        super(HttpStatus.CONFLICT,
                ErrorCodes.DRIVER_BUSY,
                "Driver is on a ride",
                "Cannot go offline while assigned to ride " + rideId
                        + "; complete or cancel it first");
    }
}
