package lk.ridelink.account.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lk.ridelink.account.domain.AccountStatus;
import lk.ridelink.account.domain.Role;
import lk.ridelink.account.dto.AccountResponse;
import lk.ridelink.account.dto.ChangePasswordRequest;
import lk.ridelink.account.dto.ChangeStatusRequest;
import lk.ridelink.account.dto.UpdateProfileRequest;
import lk.ridelink.account.service.AccountService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Account profile endpoints.
 *
 * <p>Note the split in how authorisation is expressed: role checks that are the same for
 * every request use {@code @PreAuthorize} here, while checks that depend on <em>which</em>
 * record is being touched ("is this my account?") live in the service layer, because an
 * annotation cannot see the data.</p>
 */
@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Accounts", description = "Profile management and admin account administration")
@SecurityRequirement(name = "bearerAuth")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get my profile",
            description = "Returns the profile of whoever the bearer token identifies.")
    @ApiResponse(responseCode = "200", description = "Profile returned")
    public ResponseEntity<AccountResponse> getOwnProfile() {
        return ResponseEntity.ok(accountService.getOwnProfile());
    }

    @PutMapping("/me")
    @Operation(summary = "Update my profile",
            description = "Updates name and phone only. E-mail and role are not changeable: "
                    + "e-mail is the login identifier and role is an authorisation decision.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content)
    })
    public ResponseEntity<AccountResponse> updateOwnProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(accountService.updateOwnProfile(request));
    }

    @PatchMapping("/me/password")
    @Operation(summary = "Change my password",
            description = "Requires the current password, so a stolen token alone cannot "
                    + "lock the real owner out.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Password changed"),
            @ApiResponse(responseCode = "400", description = "New password fails the policy", content = @Content),
            @ApiResponse(responseCode = "401", description = "Current password is wrong", content = @Content)
    })
    public ResponseEntity<Void> changeOwnPassword(@Valid @RequestBody ChangePasswordRequest request) {
        accountService.changeOwnPassword(request);
        // 204: the caller already knows what they set; echoing it back adds nothing.
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{accountId}")
    @Operation(summary = "Get an account by id",
            description = "Readable by an admin or by the account holder. Anyone else gets 403.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Account returned"),
            @ApiResponse(responseCode = "403", description = "Not an admin and not the owner", content = @Content),
            @ApiResponse(responseCode = "404", description = "No such account", content = @Content)
    })
    public ResponseEntity<AccountResponse> getById(@PathVariable UUID accountId) {
        return ResponseEntity.ok(accountService.getById(accountId));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List accounts (admin)",
            description = "Paginated, with optional role and status filters. "
                    + "Example: ?role=DRIVER&status=ACTIVE&page=0&size=20&sort=createdAt,desc")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of accounts"),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin", content = @Content)
    })
    public ResponseEntity<Page<AccountResponse>> search(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) AccountStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(accountService.search(role, status, pageable));
    }

    @PatchMapping("/{accountId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Suspend, reactivate or deactivate an account (admin)",
            description = "Publishes 'account.status.changed' when the status actually moves, "
                    + "which forces a suspended driver offline in the Driver service. "
                    + "An admin may not change their own status.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status changed"),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin", content = @Content),
            @ApiResponse(responseCode = "404", description = "No such account", content = @Content),
            @ApiResponse(responseCode = "422", description = "Admin tried to change their own status", content = @Content)
    })
    public ResponseEntity<AccountResponse> changeStatus(@PathVariable UUID accountId,
                                                        @Valid @RequestBody ChangeStatusRequest request) {
        return ResponseEntity.ok(accountService.changeStatus(accountId, request));
    }
}
