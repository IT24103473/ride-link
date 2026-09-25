package lk.ridelink.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lk.ridelink.payment.domain.VehicleType;

@Schema(description = "Request for a fare quote")
public record FareEstimateRequest(

        @Valid
        @NotNull(message = "Pickup is required")
        LocationRequest pickup,

        @Valid
        @NotNull(message = "Destination is required")
        LocationRequest destination,

        @Schema(example = "CAR")
        @NotNull(message = "Vehicle type is required")
        VehicleType vehicleType
) {
}
