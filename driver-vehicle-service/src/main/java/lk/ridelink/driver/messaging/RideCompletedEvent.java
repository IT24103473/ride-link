package lk.ridelink.driver.messaging;

import java.util.UUID;

/**
 * Payload of {@code ride.completed}.
 *
 * <p>This service needs only the ride and driver ids, so the many other fields the Ride
 * service publishes (locations, distances, timestamps) are simply not declared here.
 * Jackson ignores them, which is the point of a per-service copy: each consumer reads the
 * subset it cares about.</p>
 */
public record RideCompletedEvent(
        UUID rideId,
        UUID driverId) {
}
