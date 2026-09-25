package lk.ridelink.payment.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Payload of {@code ride.completed}, as consumed by this service.
 *
 * <p>This is where the final fare comes from: the real elapsed time between
 * {@code startedAt} and {@code completedAt}, and the driver's reported distance when they
 * gave one.</p>
 *
 * @param actualDistanceKm null when the driver reported no distance; the estimate is then
 *                         used instead
 */
public record RideCompletedEvent(
        UUID rideId,
        UUID passengerId,
        UUID driverId,
        String vehicleType,
        Double estimatedDistanceKm,
        Double actualDistanceKm,
        Instant startedAt,
        Instant completedAt,
        UUID fareEstimateId,
        String paymentMethod) {
}
