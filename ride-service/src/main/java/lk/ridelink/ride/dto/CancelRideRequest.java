package lk.ridelink.ride.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Cancel a ride")
public record CancelRideRequest(

        @Schema(example = "Plans changed")
        @NotBlank(message = "A cancellation reason is required")
        @Size(max = 255, message = "Reason must be at most 255 characters")
        String reason
) {
}
