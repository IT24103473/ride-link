package lk.ridelink.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lk.ridelink.payment.domain.FareType;
import lk.ridelink.payment.domain.PaymentMethod;

/**
 * Proof of payment.
 *
 * <p>Available only once a payment has succeeded, so a receipt can never be produced for
 * an unpaid or failed charge.</p>
 */
@Schema(description = "Receipt for a completed payment")
public record ReceiptResponse(
        @Schema(example = "RL-2026-000123")
        String receiptNumber,
        UUID paymentId,
        UUID rideId,
        UUID passengerId,
        UUID driverId,
        FareType type,
        FareBreakdownResponse breakdown,
        BigDecimal amountPaid,
        String currency,
        PaymentMethod method,
        Instant paidAt
) {
}
