package lk.ridelink.ride.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lk.ridelink.ride.domain.CancelledBy;
import lk.ridelink.ride.domain.PaymentMethod;
import lk.ridelink.ride.domain.PaymentStatus;
import lk.ridelink.ride.domain.RideStatus;
import lk.ridelink.ride.domain.ServiceArea;
import lk.ridelink.ride.domain.VehicleType;

@Schema(description = "A ride and its current state")
public record RideResponse(
        UUID id,
        UUID passengerId,
        @Schema(description = "Null until a driver is assigned; cleared again if they reject")
        UUID driverId,
        VehicleType vehicleType,
        ServiceArea serviceArea,
        LocationResponse pickup,
        LocationResponse destination,
        RideStatus status,
        PaymentMethod paymentMethod,

        @Schema(description = "The quote snapshotted at request time; the final fare may differ")
        UUID fareEstimateId,
        BigDecimal estimatedFare,
        BigDecimal estimatedDistanceKm,
        Integer estimatedDurationMin,
        String currency,

        Instant requestedAt,
        Instant assignedAt,
        Instant acceptedAt,
        Instant startedAt,
        Instant completedAt,
        Instant cancelledAt,
        CancelledBy cancelledBy,
        String cancellationReason,

        @Schema(description = "Read-only copy kept current by payment events; "
                + "the Fare & Payment service owns the real value")
        PaymentStatus paymentStatus
) {
}
