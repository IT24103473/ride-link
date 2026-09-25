package lk.ridelink.driver.service;

import java.util.List;
import java.util.UUID;
import lk.ridelink.driver.domain.ServiceArea;
import lk.ridelink.driver.domain.VehicleType;
import lk.ridelink.driver.dto.DriverCandidateResponse;

/**
 * The internal API the Ride service depends on: find candidates, then reserve one.
 *
 * <p>Split from {@link DriverService} because it serves a different client (another
 * service, not a person), has a different authorisation rule (SERVICE role only), and
 * changes for different reasons.</p>
 */
public interface DriverMatchingService {

    /**
     * Eligible drivers near a pickup point, ranked best-first.
     *
     * <p>The returned order is part of the contract: the Ride service attempts
     * reservations in exactly this sequence.</p>
     */
    List<DriverCandidateResponse> findAvailable(double lat, double lng, VehicleType vehicleType,
                                                ServiceArea serviceArea, Integer radiusKm, Integer limit);

    /**
     * Moves a driver AVAILABLE to BUSY for a ride.
     *
     * @throws lk.ridelink.driver.exception.DriverNotAvailableException if the driver was
     *         taken first; the caller should try the next candidate
     */
    void reserve(UUID driverId, UUID rideId);

    /**
     * Frees a driver reserved for this ride. Idempotent: a driver who is not reserved for
     * this particular ride is left untouched and no error is raised, because this is
     * called both synchronously on reject and from at-least-once event consumers.
     */
    void release(UUID driverId, UUID rideId);
}
