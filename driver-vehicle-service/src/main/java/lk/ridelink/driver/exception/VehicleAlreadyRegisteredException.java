package lk.ridelink.driver.exception;

import org.springframework.http.HttpStatus;

public class VehicleAlreadyRegisteredException extends ApiException {

    public VehicleAlreadyRegisteredException(String registrationNumber) {
        super(HttpStatus.CONFLICT,
                ErrorCodes.VEHICLE_ALREADY_REGISTERED,
                "Vehicle already registered",
                "A vehicle with registration " + registrationNumber + " already exists");
    }
}
