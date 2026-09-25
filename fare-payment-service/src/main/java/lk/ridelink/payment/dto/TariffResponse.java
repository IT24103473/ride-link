package lk.ridelink.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import lk.ridelink.payment.domain.VehicleType;

/**
 * The published pricing rule.
 *
 * <p>Exposing the formula and the rates through the API is deliberate: the assignment asks
 * for the fare rule to be clearly documented, and a rule a client can read is far harder
 * to get wrong than one buried in a report.</p>
 */
@Schema(description = "Published fare rule and per-vehicle rates")
public record TariffResponse(

        @Schema(example = "LKR")
        String currency,

        @Schema(description = "Human-readable statement of the formula")
        String formula,

        @Schema(description = "Straight-line distance is multiplied by this to approximate a road route",
                example = "1.3")
        BigDecimal roadWindingFactor,

        @Schema(description = "Assumed average city speed used to derive estimated duration",
                example = "25")
        BigDecimal averageSpeedKmh,

        @Schema(description = "How long a quote remains valid", example = "15")
        int estimateValidityMinutes,

        @Schema(description = "Charged only when a passenger cancels after a driver had accepted",
                example = "100.00")
        BigDecimal cancellationFee,

        List<VehicleTariff> tariffs
) {

    @Schema(description = "Rates for one vehicle class")
    public record VehicleTariff(
            VehicleType vehicleType,
            BigDecimal base,
            BigDecimal perKm,
            BigDecimal perMin,
            @Schema(description = "Floor price; charged when the computed fare falls below it")
            BigDecimal minimum
    ) {
    }
}
