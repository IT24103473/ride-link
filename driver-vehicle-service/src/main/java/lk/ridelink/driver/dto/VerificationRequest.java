package lk.ridelink.driver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lk.ridelink.driver.domain.VerificationStatus;

@Schema(description = "Admin decision on a driver's licence check")
public record VerificationRequest(

        @Schema(example = "VERIFIED", allowableValues = {"VERIFIED", "REJECTED"})
        @NotNull(message = "Verification status is required")
        VerificationStatus status
) {
}
