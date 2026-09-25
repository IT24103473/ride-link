package lk.ridelink.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Client-credentials grant for service-to-service calls.
 *
 * <p>A token issued from this request carries {@code role=SERVICE}, which is the only
 * role accepted by the {@code /api/v1/internal/**} endpoints. Passengers and drivers
 * therefore cannot reserve drivers directly.</p>
 */
@Schema(description = "Service client credentials")
public record ServiceTokenRequest(

        @Schema(example = "ride-service")
        @NotBlank(message = "Client id is required")
        String clientId,

        @NotBlank(message = "Client secret is required")
        String clientSecret
) {
}
