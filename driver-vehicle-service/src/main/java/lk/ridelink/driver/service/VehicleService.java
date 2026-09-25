package lk.ridelink.driver.service;

import java.util.List;
import java.util.UUID;
import lk.ridelink.driver.dto.VehicleRequest;
import lk.ridelink.driver.dto.VehicleResponse;

/** Vehicle registration and activation for the calling driver. */
public interface VehicleService {

    VehicleResponse addOwnVehicle(VehicleRequest request);

    List<VehicleResponse> listOwnVehicles();

    VehicleResponse updateOwnVehicle(UUID vehicleId, VehicleRequest request);

    /** Activates one vehicle and deactivates the driver's others, so exactly one is live. */
    VehicleResponse activateOwnVehicle(UUID vehicleId);
}
