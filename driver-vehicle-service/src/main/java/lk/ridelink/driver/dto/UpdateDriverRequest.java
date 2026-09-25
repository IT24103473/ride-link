package lk.ridelink.driver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lk.ridelink.driver.domain.ServiceArea;

@Schema(description = "Licence and operating area details a driver maintains themselves")
public record UpdateDriverRequest(

        @Schema(example = "B1234567")
        @NotBlank(message = "Licence number is required")
        @Size(max = 30, message = "Licence number must be at most 30 characters")
        String licenceNumber,

        @Schema(example = "2030-12-31", description = "Must be in the future")
        @NotNull(message = "Licence expiry is required")
        @Future(message = "Licence expiry must be a future date")
        LocalDate licenceExpiry,

        @Schema(example = "NEGOMBO")
        @NotNull(message = "Service area is required")
        ServiceArea serviceArea
) {
}
