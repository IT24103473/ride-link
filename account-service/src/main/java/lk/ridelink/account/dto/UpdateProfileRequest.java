package lk.ridelink.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Self-service profile update. E-mail and role are deliberately absent: e-mail is the
 * login identifier and role is an authorisation decision, so neither may be changed
 * by the account holder.
 */
@Schema(description = "Fields a user may change about their own profile")
public record UpdateProfileRequest(

        @Schema(example = "Nimal Perera")
        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
        String fullName,

        @Schema(example = "+94771234567")
        @NotBlank(message = "Phone is required")
        @Pattern(regexp = "^\\+94\\d{9}$",
                message = "Phone must be a Sri Lankan mobile number, e.g. +94771234567")
        String phone
) {
}
