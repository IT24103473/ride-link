package lk.ridelink.ride.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {

    public NotFoundException(String detail) {
        super(HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND, "Resource not found", detail);
    }

    public static NotFoundException ride(Object rideId) {
        return new NotFoundException("No ride exists with id " + rideId);
    }
}
