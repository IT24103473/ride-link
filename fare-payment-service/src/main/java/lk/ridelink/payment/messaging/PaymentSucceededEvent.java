package lk.ridelink.payment.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Payload of {@code payment.succeeded}.
 *
 * <p>Carries the receipt number so the Ride service can show proof of payment without
 * calling back into this service.</p>
 */
public record PaymentSucceededEvent(
        UUID paymentId,
        UUID rideId,
        BigDecimal amount,
        String currency,
        String receiptNumber,
        Instant paidAt) {
}
