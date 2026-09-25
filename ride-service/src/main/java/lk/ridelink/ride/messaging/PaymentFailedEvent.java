package lk.ridelink.ride.messaging;

import java.util.UUID;

/** Payload of {@code payment.failed}. */
public record PaymentFailedEvent(UUID paymentId, UUID rideId, String failureReason) {
}
