package lk.ridelink.payment.messaging;

import java.util.UUID;

/**
 * Payload of {@code ride.cancelled}.
 *
 * <p>{@code previousStatus} and {@code cancelledBy} together decide whether a fee is due:
 * only a PASSENGER cancelling from ACCEPTED is charged, because that is the point at which
 * a driver had already committed and begun travelling to the pickup.</p>
 */
public record RideCancelledEvent(
        UUID rideId,
        UUID passengerId,
        UUID driverId,
        String previousStatus,
        String cancelledBy,
        String vehicleType,
        String paymentMethod) {

    private static final String PASSENGER = "PASSENGER";
    private static final String ACCEPTED = "ACCEPTED";

    /** The documented cancellation-fee rule, in one place. */
    public boolean incursCancellationFee() {
        return PASSENGER.equalsIgnoreCase(cancelledBy) && ACCEPTED.equalsIgnoreCase(previousStatus);
    }
}
