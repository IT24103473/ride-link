package lk.ridelink.driver.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import lk.ridelink.driver.domain.ServiceArea;
import lk.ridelink.driver.domain.VehicleType;
import lk.ridelink.driver.dto.DriverCandidateResponse;
import lk.ridelink.driver.dto.ReservationRequest;
import lk.ridelink.driver.service.DriverMatchingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service endpoints, called only by the Ride service.
 *
 * <p>Reserving and releasing require {@code ROLE_SERVICE}, which is issued solely by the
 * Account service's client-credentials endpoint. A passenger's token - however valid -
 * cannot reach them, which is what stops anyone reserving drivers directly and taking the
 * fleet out of circulation. The Postman collection demonstrates that refusal as a
 * negative case.</p>
 *
 * <p>The read-only search additionally admits {@code ROLE_ADMIN}, so an operator can
 * troubleshoot why no driver was found without holding a service secret. Writes stay
 * SERVICE-only, because a reservation must remain in step with the Ride service's view of
 * the trip.</p>
 *
 * <p>The restriction is applied twice: a coarse path rule in {@code SecurityConfig} keeps
 * passengers and drivers out of everything here even if a new endpoint's author forgets
 * its annotation, and the per-method {@code @PreAuthorize} narrows it from there.</p>
 */
@RestController
@RequestMapping("/api/v1/internal/drivers")
@Tag(name = "Internal (service-to-service)",
        description = "Driver matching and reservation. Requires a SERVICE token; passengers and drivers get 403.")
@SecurityRequirement(name = "bearerAuth")
public class InternalDriverController {

    private final DriverMatchingService matchingService;

    public InternalDriverController(DriverMatchingService matchingService) {
        this.matchingService = matchingService;
    }

    @GetMapping("/available")
    @PreAuthorize("hasAnyRole('SERVICE', 'ADMIN')")
    @Operation(summary = "Find drivers available for a pickup",
            description = """
                    Returns eligible drivers ranked nearest-first, with the longest-waiting \
                    driver winning a tie. Eligibility means: AVAILABLE, VERIFIED, account \
                    active, licence not expired, an active vehicle of the requested type, the \
                    same service area, a position updated within the last 30 minutes, and \
                    within radiusKm of the pickup.

                    The order is part of the contract: the caller attempts reservations in \
                    exactly this sequence.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ranked candidates, possibly empty"),
            @ApiResponse(responseCode = "403", description = "Caller does not hold a SERVICE token", content = @Content)
    })
    public ResponseEntity<List<DriverCandidateResponse>> findAvailable(
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double lat,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double lng,
            @RequestParam VehicleType vehicleType,
            @RequestParam ServiceArea serviceArea,
            // Capped at 20 in the service as well, so a crafted value cannot widen the scan.
            @RequestParam(required = false) @Min(1) @Max(20) Integer radiusKm,
            @RequestParam(required = false) @Min(1) @Max(20) Integer limit) {
        return ResponseEntity.ok(
                matchingService.findAvailable(lat, lng, vehicleType, serviceArea, radiusKm, limit));
    }

    @PostMapping("/{driverId}/reserve")
    @PreAuthorize("hasRole('SERVICE')")
    @Operation(summary = "Reserve a driver for a ride",
            description = "Moves the driver AVAILABLE to BUSY under an optimistic lock. "
                    + "A 409 means another ride took them first; the caller should try the "
                    + "next candidate rather than treat it as an error.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Driver reserved"),
            @ApiResponse(responseCode = "403", description = "Caller does not hold a SERVICE token", content = @Content),
            @ApiResponse(responseCode = "404", description = "No such driver", content = @Content),
            @ApiResponse(responseCode = "409", description = "DRIVER_NOT_AVAILABLE: taken by another ride", content = @Content)
    })
    public ResponseEntity<Void> reserve(@PathVariable UUID driverId,
                                        @Valid @RequestBody ReservationRequest request) {
        matchingService.reserve(driverId, request.rideId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{driverId}/release")
    @PreAuthorize("hasRole('SERVICE')")
    @Operation(summary = "Release a driver from a ride",
            description = "Idempotent: the driver is freed only if they are currently reserved "
                    + "for this exact ride, so a redelivered or late release cannot free a "
                    + "driver who has already started another trip. Safe to call repeatedly.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Released, or already not on this ride"),
            @ApiResponse(responseCode = "403", description = "Caller does not hold a SERVICE token", content = @Content),
            @ApiResponse(responseCode = "404", description = "No such driver", content = @Content)
    })
    public ResponseEntity<Void> release(@PathVariable UUID driverId,
                                        @Valid @RequestBody ReservationRequest request) {
        matchingService.release(driverId, request.rideId());
        return ResponseEntity.noContent().build();
    }
}
