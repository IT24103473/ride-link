package lk.ridelink.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * The itemised fare.
 *
 * <p>Returned in full rather than as a single total so a passenger can see exactly how a
 * price was reached - and, when the minimum applied, why the parts do not sum to the
 * total.</p>
 */
@Schema(description = "Itemised fare calculation")
public record FareBreakdownResponse(

        @Schema(description = "Road distance, already adjusted for winding", example = "54.08")
        BigDecimal distanceKm,

        @Schema(example = "130")
        Integer durationMin,

        BigDecimal baseFare,

        @Schema(description = "perKm rate x distance")
        BigDecimal distanceFare,

        @Schema(description = "perMin rate x duration")
        BigDecimal timeFare,

        @Schema(description = "Floor price for this vehicle type")
        BigDecimal minimumFare,

        @Schema(description = "True when the computed fare fell below the minimum, so the minimum was charged")
        boolean minimumFareApplied,

        BigDecimal total
) {
}
