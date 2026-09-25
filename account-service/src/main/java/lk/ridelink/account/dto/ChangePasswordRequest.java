package lk.ridelink.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Password change; the current password must be supplied")
public record ChangePasswordRequest(

        @Schema(description = "Required so a stolen token alone cannot change the password")
        @NotBlank(message = "Current password is required")
        String currentPassword,

        @Schema(example = "NewPassw0rd123")
        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                message = "Password must contain at least one letter and one digit")
        String newPassword
) {
}
