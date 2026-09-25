package lk.ridelink.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lk.ridelink.account.domain.AccountStatus;

@Schema(description = "Admin-driven account status change")
public record ChangeStatusRequest(

        @Schema(example = "SUSPENDED")
        @NotNull(message = "Status is required")
        AccountStatus status,

        @Schema(description = "Why the status changed; recorded for the audit trail",
                example = "Repeated ride cancellations")
        @Size(max = 255, message = "Reason must be at most 255 characters")
        String reason
) {
}
