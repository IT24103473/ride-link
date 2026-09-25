package lk.ridelink.ride.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lk.ridelink.ride.domain.PaymentMethod;
import lk.ridelink.ride.domain.ServiceArea;
import lk.ridelink.ride.domain.VehicleType;

@Schema(description = "Request a ride")
public record CreateRideRequest(

        @Valid
        @NotNull(message = "Pickup is required")
        LocationRequest pickup,

        @Valid
        @NotNull(message = "Destination is required")
        LocationRequest destination,

        @Schema(example = "CAR")
        @NotNull(message = "Vehicle type is required")
        VehicleType vehicleType,

        @Schema(example = "NEGOMBO", description = "Drivers are matched within one area only")
        @NotNull(message = "Service area is required")
        ServiceArea serviceArea,

        @Schema(example = "CARD")
        @NotNull(message = "Payment method is required")
        PaymentMethod paymentMethod
) {
}
