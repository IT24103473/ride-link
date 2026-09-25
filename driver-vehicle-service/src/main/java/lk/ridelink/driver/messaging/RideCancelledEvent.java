package lk.ridelink.driver.messaging;

import java.util.UUID;

/**
 * Payload of {@code ride.cancelled}.
 *
 * <p>{@code driverId} is null when the ride was cancelled while still REQUESTED, i.e.
 * before anyone was reserved - in which case there is nothing to release.
 */
public record RideCancelledEvent(
        UUID rideId,
        UUID driverId) {
}
