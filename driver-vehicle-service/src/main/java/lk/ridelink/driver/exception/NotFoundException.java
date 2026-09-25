package lk.ridelink.driver.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {

    public NotFoundException(String detail) {
        super(HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND, "Resource not found", detail);
    }

    public static NotFoundException driver(Object driverId) {
        return new NotFoundException("No driver profile exists with id " + driverId);
    }

    public static NotFoundException vehicle(Object vehicleId) {
        return new NotFoundException("No vehicle exists with id " + vehicleId);
    }
}
