package lk.ridelink.driver.mapper;

import lk.ridelink.driver.domain.DriverCandidate;
import lk.ridelink.driver.domain.DriverProfile;
import lk.ridelink.driver.domain.Vehicle;
import lk.ridelink.driver.dto.DriverCandidateResponse;
import lk.ridelink.driver.dto.DriverProfileResponse;
import lk.ridelink.driver.dto.DriverSummaryResponse;
import lk.ridelink.driver.dto.VehicleResponse;
import org.springframework.stereotype.Component;

/**
 * Entity to DTO conversion.
 *
 * <p>The distinction that matters here is {@link #toProfile} versus {@link #toSummary}:
 * the profile includes the licence number and exact position and is only ever returned to
 * the driver themselves or an admin, while the summary is what any authenticated caller
 * may see. Keeping both in one class makes that difference easy to review.</p>
 */
@Component
public class DriverMapper {

    public VehicleResponse toVehicle(Vehicle vehicle) {
        if (vehicle == null) {
            return null;
        }
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getRegistrationNumber(),
                vehicle.getType(),
                vehicle.getMake(),
                vehicle.getModel(),
                vehicle.getColour(),
                vehicle.getSeats(),
                vehicle.getYear(),
                vehicle.isActive());
    }

    /** Full profile: licence details and position included. Driver or admin only. */
    public DriverProfileResponse toProfile(DriverProfile driver, Vehicle activeVehicle) {
        return new DriverProfileResponse(
                driver.getDriverId(),
                driver.getFullName(),
                driver.getPhone(),
                driver.getLicenceNumber(),
                driver.getLicenceExpiry(),
                driver.getServiceArea(),
                driver.getVerificationStatus(),
                driver.isAccountActive(),
                driver.getAvailability(),
                driver.getAvailableSince(),
                driver.getCurrentLat(),
                driver.getCurrentLng(),
                driver.getLocationUpdatedAt(),
                driver.getCurrentRideId(),
                driver.getCompletedTrips(),
                toVehicle(activeVehicle));
    }

    /** Public summary: no licence number and no coordinates. */
    public DriverSummaryResponse toSummary(DriverProfile driver, Vehicle activeVehicle) {
        return new DriverSummaryResponse(
                driver.getDriverId(),
                driver.getFullName(),
                driver.getServiceArea(),
                driver.getVerificationStatus(),
                driver.getCompletedTrips(),
                DriverSummaryResponse.PLACEHOLDER_RATING,
                toVehicle(activeVehicle));
    }

    public DriverCandidateResponse toCandidate(DriverCandidate candidate) {
        return new DriverCandidateResponse(
                candidate.driverId(),
                candidate.fullName(),
                // Two decimals is plenty for a dispatch decision and keeps the JSON tidy.
                Math.round(candidate.distanceKm() * 100.0) / 100.0,
                candidate.availableSince(),
                candidate.vehicleType(),
                candidate.registrationNumber(),
                candidate.completedTrips());
    }
}
