package lk.ridelink.driver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lk.ridelink.driver.domain.VehicleType;

@Schema(description = "A registered vehicle")
public record VehicleResponse(
        UUID id,
        String registrationNumber,
        VehicleType type,
        String make,
        String model,
        String colour,
        int seats,
        int year,
        @Schema(description = "Only the active vehicle is used for matching")
        boolean active
) {
}
