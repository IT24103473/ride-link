package lk.ridelink.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import lk.ridelink.payment.domain.VehicleType;

@Schema(description = "A fare quote, valid until expiresAt")
public record FareEstimateResponse(
        UUID id,
        UUID passengerId,
        LocationResponse pickup,
        LocationResponse destination,
        VehicleType vehicleType,
        FareBreakdownResponse breakdown,
        @Schema(example = "LKR")
        String currency,
        Instant createdAt,
        @Schema(description = "After this the quote must be refreshed")
        Instant expiresAt
) {
}
