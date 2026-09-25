package lk.ridelink.payment.messaging;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payload of {@code payment.failed}.
 *
 * <p>Not terminal: a later {@code payment.succeeded} for the same ride supersedes it,
 * because a declined card can be retried.</p>
 */
public record PaymentFailedEvent(
        UUID paymentId,
        UUID rideId,
        BigDecimal amount,
        String currency,
        String failureReason) {
}
