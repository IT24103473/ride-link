package lk.ridelink.driver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lk.ridelink.driver.domain.Availability;
import lk.ridelink.driver.domain.ServiceArea;
import lk.ridelink.driver.domain.VerificationStatus;

/** Full profile, returned to the driver themselves and to admins. */
@Schema(description = "Driver profile with the currently active vehicle")
public record DriverProfileResponse(
        UUID driverId,
        String fullName,
        String phone,
        String licenceNumber,
        LocalDate licenceExpiry,
        ServiceArea serviceArea,
        VerificationStatus verificationStatus,
        @Schema(description = "False when the Account service has suspended this account")
        boolean accountActive,
        Availability availability,
        Instant availableSince,
        Double currentLat,
        Double currentLng,
        Instant locationUpdatedAt,
        @Schema(description = "Set only while the driver is BUSY")
        UUID currentRideId,
        int completedTrips,
        VehicleResponse activeVehicle
) {
}
