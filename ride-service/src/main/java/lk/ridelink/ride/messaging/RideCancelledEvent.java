package lk.ridelink.ride.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Payload of {@code ride.cancelled}.
 *
 * <p>{@code previousStatus} is the whole reason this event carries more than an id: the
 * cancellation-fee rule keys on the status the ride held immediately before cancellation,
 * and by the time the event is published the ride is already CANCELLED. Without it, the
 * Fare service could not tell a late cancellation from an early one.</p>
 *
 * @param driverId null when the ride was cancelled before anyone was assigned
 */
public record RideCancelledEvent(
        UUID rideId,
        UUID passengerId,
        UUID driverId,
        String previousStatus,
        String cancelledBy,
        Instant cancelledAt,
        String vehicleType,
        String paymentMethod) {
}
