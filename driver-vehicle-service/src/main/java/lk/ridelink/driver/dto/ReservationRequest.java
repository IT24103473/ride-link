package lk.ridelink.driver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Reserve or release a driver for a specific ride.
 *
 * <p>The ride id is required on release too, and is checked: a driver is only freed when
 * they are actually reserved for <em>that</em> ride. Without it, a late-arriving release
 * for an old ride could free a driver who has since started a new one.</p>
 */
@Schema(description = "Reserve or release a driver for a ride")
public record ReservationRequest(

        @NotNull(message = "Ride id is required")
        UUID rideId
) {
}
