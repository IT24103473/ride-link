package lk.ridelink.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import lk.ridelink.payment.domain.FareType;
import lk.ridelink.payment.domain.VehicleType;

@Schema(description = "What a ride actually cost")
public record FinalFareResponse(
        UUID id,
        UUID rideId,
        FareType type,
        VehicleType vehicleType,
        FareBreakdownResponse breakdown,
        String currency,
        Instant createdAt
) {
}
