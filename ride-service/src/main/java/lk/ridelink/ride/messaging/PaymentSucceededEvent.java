package lk.ridelink.ride.messaging;

import java.util.UUID;

/** Payload of {@code payment.succeeded}; only the fields this service uses are mapped. */
public record PaymentSucceededEvent(UUID paymentId, UUID rideId, String receiptNumber) {
}
