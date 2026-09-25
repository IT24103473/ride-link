package lk.ridelink.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "A place name with simulated coordinates")
public record LocationRequest(

        @Schema(example = "Colombo Fort")
        @NotBlank(message = "Location name is required")
        @Size(max = 100, message = "Location name must be at most 100 characters")
        String name,

        @Schema(example = "6.9344")
        @NotNull(message = "Latitude is required")
        @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
        Double lat,

        @Schema(example = "79.8428")
        @NotNull(message = "Longitude is required")
        @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
        Double lng
) {
}
