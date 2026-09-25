package lk.ridelink.driver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import lk.ridelink.driver.domain.VehicleType;

/**
 * One ranked candidate returned to the Ride service.
 *
 * <p>The Ride service attempts a reservation on these in the order given, so the ordering
 * of the list is part of the contract, not a presentational detail.</p>
 */
@Schema(description = "A driver eligible for a ride, ranked nearest-first")
public record DriverCandidateResponse(
        UUID driverId,
        String fullName,
        @Schema(description = "Straight-line distance from the pickup point", example = "1.84")
        double distanceKm,
        @Schema(description = "When the driver became available; the tie-break on equal distance")
        Instant availableSince,
        VehicleType vehicleType,
        String registrationNumber,
        int completedTrips
) {
}
