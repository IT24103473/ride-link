package lk.ridelink.ride.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

/**
 * Optional details supplied when a driver completes a ride.
 *
 * <p>The whole body may be omitted. When no distance is reported, the final fare falls
 * back to the estimated distance, so a driver forgetting to enter it never blocks
 * completion.</p>
 */
@Schema(description = "Optional completion details")
public record CompleteRideRequest(

        @Schema(description = "Distance actually driven; the estimate is used if omitted",
                example = "43.2")
        @DecimalMin(value = "0.1", message = "Actual distance must be greater than 0")
        @DecimalMax(value = "200.0", message = "Actual distance must be at most 200 km")
        BigDecimal actualDistanceKm
) {
}
