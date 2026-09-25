package lk.ridelink.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Credentials for password login")
public record LoginRequest(

        @Schema(example = "nimal@ridelink.test")
        @NotBlank(message = "Email is required")
        String email,

        @Schema(example = "Passw0rd123")
        @NotBlank(message = "Password is required")
        String password
) {
}
