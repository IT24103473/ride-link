package lk.ridelink.driver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lk.ridelink.driver.domain.ServiceArea;
import lk.ridelink.driver.domain.VerificationStatus;

/**
 * Public view of a driver, returned to any authenticated caller - typically a passenger
 * checking who has been assigned to their ride.
 *
 * <p>The licence number is omitted: a passenger has no reason to see it, and it is
 * personal data. Admins read the full profile through their own endpoint instead.</p>
 */
@Schema(description = "Public driver summary; excludes licence details")
public record DriverSummaryResponse(
        UUID driverId,
        String fullName,
        ServiceArea serviceArea,
        VerificationStatus verificationStatus,
        int completedTrips,
        @Schema(description = "Placeholder until ratings are implemented", example = "5.0")
        double rating,
        VehicleResponse activeVehicle
) {
    /** Fixed until a ratings feature exists; documented so the value is not mistaken for real data. */
    public static final double PLACEHOLDER_RATING = 5.0;
}
