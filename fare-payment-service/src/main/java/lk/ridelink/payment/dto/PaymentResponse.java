package lk.ridelink.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lk.ridelink.payment.domain.FailureReason;
import lk.ridelink.payment.domain.FareType;
import lk.ridelink.payment.domain.PaymentMethod;
import lk.ridelink.payment.domain.PaymentStatus;

@Schema(description = "A charge against a ride")
public record PaymentResponse(
        UUID id,
        UUID rideId,
        UUID passengerId,
        UUID driverId,
        @Schema(description = "TRIP for a completed journey, CANCELLATION_FEE for a late cancellation")
        FareType type,
        BigDecimal amount,
        String currency,
        PaymentMethod method,
        PaymentStatus status,
        @Schema(description = "Set only once the payment has succeeded", example = "RL-2026-000123")
        String receiptNumber,
        Instant paidAt,
        @Schema(description = "Set only when the last attempt failed; the payment may be retried")
        FailureReason failureReason,
        Instant createdAt
) {
}
