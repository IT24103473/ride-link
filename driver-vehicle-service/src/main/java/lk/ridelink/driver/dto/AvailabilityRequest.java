package lk.ridelink.driver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lk.ridelink.driver.domain.Availability;

/**
 * Availability change requested by the driver.
 *
 * <p>Only AVAILABLE and OFFLINE are accepted; BUSY is rejected by the service. BUSY is
 * entered solely by a reservation from the Ride service, so a driver cannot mark
 * themselves free - or busy - to dodge dispatch.</p>
 */
@Schema(description = "Driver availability change; only AVAILABLE or OFFLINE are accepted")
public record AvailabilityRequest(

        @Schema(example = "AVAILABLE", allowableValues = {"AVAILABLE", "OFFLINE"})
        @NotNull(message = "Availability is required")
        Availability availability
) {
}
