package lk.ridelink.driver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lk.ridelink.driver.domain.VehicleType;

@Schema(description = "Vehicle registration details")
public record VehicleRequest(

        @Schema(example = "WP CAB-1234")
        @NotBlank(message = "Registration number is required")
        @Size(max = 20, message = "Registration number must be at most 20 characters")
        String registrationNumber,

        @Schema(example = "CAR")
        @NotNull(message = "Vehicle type is required")
        VehicleType type,

        @Schema(example = "Toyota")
        @NotBlank(message = "Make is required")
        @Size(max = 40, message = "Make must be at most 40 characters")
        String make,

        @Schema(example = "Axio")
        @NotBlank(message = "Model is required")
        @Size(max = 40, message = "Model must be at most 40 characters")
        String model,

        @Schema(example = "Silver")
        @NotBlank(message = "Colour is required")
        @Size(max = 30, message = "Colour must be at most 30 characters")
        String colour,

        @Schema(example = "4")
        @NotNull(message = "Seats is required")
        @Min(value = 2, message = "A vehicle must have at least 2 seats")
        @Max(value = 15, message = "A vehicle may have at most 15 seats")
        Integer seats,

        @Schema(example = "2018")
        @NotNull(message = "Year is required")
        @Min(value = 1990, message = "Year must be 1990 or later")
        @Max(value = 2100, message = "Year must be 2100 or earlier")
        Integer year
) {
}
