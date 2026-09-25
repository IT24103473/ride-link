package lk.ridelink.driver.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lk.ridelink.driver.domain.Availability;
import lk.ridelink.driver.domain.ServiceArea;
import lk.ridelink.driver.domain.VerificationStatus;
import lk.ridelink.driver.dto.DriverProfileResponse;
import lk.ridelink.driver.dto.VerificationRequest;
import lk.ridelink.driver.service.DriverService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin oversight of the driver fleet.
 *
 * <p>Kept separate from {@link DriverController} because the audience and the
 * authorisation rule are different: every method here is admin-only, which is far easier
 * to verify at a glance when the admin endpoints are not interleaved with the
 * driver-facing ones.</p>
 */
@RestController
@RequestMapping("/api/v1/drivers")
@Tag(name = "Driver administration", description = "Admin-only fleet oversight and verification")
@SecurityRequirement(name = "bearerAuth")
public class DriverAdminController {

    private final DriverService driverService;

    public DriverAdminController(DriverService driverService) {
        this.driverService = driverService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List drivers (admin)",
            description = "Paginated, with optional area, availability and verification filters. "
                    + "Example: ?serviceArea=NEGOMBO&availability=AVAILABLE")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of drivers"),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin", content = @Content)
    })
    public ResponseEntity<Page<DriverProfileResponse>> search(
            @RequestParam(required = false) ServiceArea serviceArea,
            @RequestParam(required = false) Availability availability,
            @RequestParam(required = false) VerificationStatus verificationStatus,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(
                driverService.search(serviceArea, availability, verificationStatus, pageable));
    }

    @PatchMapping("/{driverId}/verification")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Verify or reject a driver (admin)",
            description = "A driver cannot go AVAILABLE until VERIFIED. Rejecting a driver who "
                    + "is currently online forces them OFFLINE immediately, so they stop being "
                    + "matched right away rather than at their next availability change.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Verification updated"),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin", content = @Content),
            @ApiResponse(responseCode = "404", description = "No such driver", content = @Content),
            @ApiResponse(responseCode = "422", description = "Attempted to set PENDING, which is not a decision", content = @Content)
    })
    public ResponseEntity<DriverProfileResponse> changeVerification(
            @PathVariable UUID driverId,
            @Valid @RequestBody VerificationRequest request) {
        return ResponseEntity.ok(driverService.changeVerification(driverId, request));
    }
}
