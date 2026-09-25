package lk.ridelink.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Short-lived token for service-to-service calls")
public record ServiceTokenResponse(

        String accessToken,

        @Schema(example = "Bearer")
        String tokenType,

        @Schema(description = "Token lifetime in seconds; short by design", example = "600")
        long expiresIn
) {
    public static ServiceTokenResponse of(String accessToken, long expiresInSeconds) {
        return new ServiceTokenResponse(accessToken, "Bearer", expiresInSeconds);
    }
}
