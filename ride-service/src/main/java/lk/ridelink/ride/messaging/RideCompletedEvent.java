package lk.ridelink.ride.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Payload of {@code ride.completed}.
 *
 * <p>Carries everything two different consumers need without either having to call back:
 * the Driver service reads the ids to release the driver, and the Fare service reads the
 * distances and timestamps to price the trip. Publishing a slightly wider payload than any
 * single consumer needs is the cost of keeping them decoupled.</p>
 *
 * @param actualDistanceKm null when the driver reported no distance
 */
public record RideCompletedEvent(
        UUID rideId,
        UUID passengerId,
        UUID driverId,
        String vehicleType,
        LocationPayload pickup,
        LocationPayload destination,
        Double estimatedDistanceKm,
        Double actualDistanceKm,
        Instant startedAt,
        Instant completedAt,
        UUID fareEstimateId,
        String paymentMethod) {
}
