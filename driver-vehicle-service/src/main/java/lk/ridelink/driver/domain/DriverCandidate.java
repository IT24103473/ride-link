package lk.ridelink.driver.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A driver who passed the eligibility filter, paired with how far they are from the
 * pickup point.
 *
 * <p>Distance is computed once during filtering and carried here rather than recomputed
 * inside the comparator, so ranking a list of candidates does no trigonometry at all.</p>
 *
 * @param availableSince used only as the tie-break: on equal distance the driver who has
 *                       been waiting longest is ranked first
 */
public record DriverCandidate(
        UUID driverId,
        String fullName,
        double distanceKm,
        Instant availableSince,
        VehicleType vehicleType,
        String registrationNumber,
        int completedTrips) {
}
