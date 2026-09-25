package lk.ridelink.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import lk.ridelink.account.domain.AccountStatus;
import lk.ridelink.account.domain.Role;

/**
 * Public view of an account.
 *
 * <p>This record is the reason controllers never return the {@code Account} entity
 * directly: there is no field here that could carry the password hash out.</p>
 */
@Schema(description = "Account profile")
public record AccountResponse(
        UUID id,
        String fullName,
        String email,
        String phone,
        Role role,
        AccountStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
