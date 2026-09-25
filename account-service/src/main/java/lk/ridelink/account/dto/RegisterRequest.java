package lk.ridelink.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Registration payload, shared by the passenger and driver endpoints.
 *
 * <p>The role is not a field: it is decided by which endpoint was called, so a caller
 * cannot register themselves as an ADMIN by crafting the body.</p>
 */
@Schema(description = "Details required to register a new account")
public record RegisterRequest(

        @Schema(example = "Nimal Perera")
        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
        String fullName,

        @Schema(example = "nimal@ridelink.test")
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        @Size(max = 150, message = "Email must be at most 150 characters")
        String email,

        @Schema(example = "+94771234567", description = "Sri Lankan mobile number")
        @NotBlank(message = "Phone is required")
        @Pattern(regexp = "^\\+94\\d{9}$",
                message = "Phone must be a Sri Lankan mobile number, e.g. +94771234567")
        String phone,

        @Schema(example = "Passw0rd123", description = "At least 8 characters, with a letter and a digit")
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                message = "Password must contain at least one letter and one digit")
        String password
) {
}
