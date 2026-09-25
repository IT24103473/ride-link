package lk.ridelink.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lk.ridelink.account.domain.Role;

/**
 * Issued token plus the few facts a client needs to decide what to show next,
 * so the caller does not have to immediately follow up with GET /accounts/me.
 */
@Schema(description = "Successful login result")
public record LoginResponse(

        @Schema(description = "Signed HS256 JWT to send as 'Authorization: Bearer <token>'")
        String accessToken,

        @Schema(example = "Bearer")
        String tokenType,

        @Schema(description = "Token lifetime in seconds", example = "3600")
        long expiresIn,

        Role role,

        UUID accountId
) {
    public static LoginResponse of(String accessToken, long expiresInSeconds, Role role, UUID accountId) {
        return new LoginResponse(accessToken, "Bearer", expiresInSeconds, role, accountId);
    }
}
