package lk.ridelink.ride.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import lk.ridelink.ride.domain.RideStatus;

@Schema(description = "One recorded status change")
public record RideStatusHistoryResponse(
        UUID id,
        @Schema(description = "Null on the row recording the ride's creation")
        RideStatus fromStatus,
        RideStatus toStatus,
        @Schema(description = "Account id that caused the change, or SYSTEM for an event-driven one")
        String changedBy,
        Instant changedAt,
        String note
) {
}
