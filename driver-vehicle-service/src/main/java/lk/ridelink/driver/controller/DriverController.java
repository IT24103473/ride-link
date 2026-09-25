package lk.ridelink.driver.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lk.ridelink.driver.dto.AvailabilityRequest;
import lk.ridelink.driver.dto.DriverProfileResponse;
import lk.ridelink.driver.dto.DriverSummaryResponse;
import lk.ridelink.driver.dto.LocationRequest;
import lk.ridelink.driver.dto.UpdateDriverRequest;
import lk.ridelink.driver.dto.VehicleRequest;
import lk.ridelink.driver.dto.VehicleResponse;
import lk.ridelink.driver.service.DriverService;
import lk.ridelink.driver.service.VehicleService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints a driver uses to manage themselves.
 *
 * <p>Everything under {@code /drivers/me} resolves the driver from the token's subject,
 * so there is no id in the path that could be tampered with to act as someone else.</p>
 */
@RestController
@RequestMapping("/api/v1/drivers")
@Tag(name = "Drivers", description = "Driver profile, vehicles, availability and location")
@SecurityRequirement(name = "bearerAuth")
public class DriverController {

    private final DriverService driverService;
    private final VehicleService vehicleService;

    public DriverController(DriverService driverService, VehicleService vehicleService) {
        this.driverService = driverService;
        this.vehicleService = vehicleService;
    }

    // --- Profile ------------------------------------------------------------

    @GetMapping("/me")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "Get my driver profile",
            description = "Includes licence details, current position and the active vehicle.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile returned"),
            @ApiResponse(responseCode = "404", description = "No profile yet; the account.driver.registered event may not have arrived", content = @Content)
    })
    public ResponseEntity<DriverProfileResponse> getOwnProfile() {
        return ResponseEntity.ok(driverService.getOwnProfile());
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "Update my licence and service area",
            description = "The licence expiry must be a future date. These fields must be set "
                    + "before the driver can go AVAILABLE.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed, e.g. licence expiry in the past", content = @Content)
    })
    public ResponseEntity<DriverProfileResponse> updateOwnProfile(
            @Valid @RequestBody UpdateDriverRequest request) {
        return ResponseEntity.ok(driverService.updateOwnProfile(request));
    }

    // --- Vehicles -----------------------------------------------------------

    @PostMapping("/me/vehicles")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "Register a vehicle",
            description = "The first vehicle a driver registers is activated automatically; "
                    + "later ones must be activated explicitly.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Vehicle registered"),
            @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content),
            @ApiResponse(responseCode = "409", description = "VEHICLE_ALREADY_REGISTERED", content = @Content)
    })
    public ResponseEntity<VehicleResponse> addVehicle(@Valid @RequestBody VehicleRequest request) {
        VehicleResponse created = vehicleService.addOwnVehicle(request);
        return ResponseEntity.created(URI.create("/api/v1/drivers/me/vehicles/" + created.id())).body(created);
    }

    @GetMapping("/me/vehicles")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "List my vehicles")
    @ApiResponse(responseCode = "200", description = "Vehicles returned")
    public ResponseEntity<List<VehicleResponse>> listVehicles() {
        return ResponseEntity.ok(vehicleService.listOwnVehicles());
    }

    @PutMapping("/me/vehicles/{vehicleId}")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "Update one of my vehicles",
            description = "The registration number is the vehicle's identity and cannot be changed.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vehicle updated"),
            @ApiResponse(responseCode = "403", description = "The vehicle belongs to another driver", content = @Content),
            @ApiResponse(responseCode = "404", description = "No such vehicle", content = @Content)
    })
    public ResponseEntity<VehicleResponse> updateVehicle(@PathVariable UUID vehicleId,
                                                         @Valid @RequestBody VehicleRequest request) {
        return ResponseEntity.ok(vehicleService.updateOwnVehicle(vehicleId, request));
    }

    @PatchMapping("/me/vehicles/{vehicleId}/activate")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "Make this my active vehicle",
            description = "Deactivates the driver's other vehicles. Matching filters on the "
                    + "active vehicle's type, so exactly one must be active.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vehicle activated"),
            @ApiResponse(responseCode = "403", description = "The vehicle belongs to another driver", content = @Content),
            @ApiResponse(responseCode = "404", description = "No such vehicle", content = @Content)
    })
    public ResponseEntity<VehicleResponse> activateVehicle(@PathVariable UUID vehicleId) {
        return ResponseEntity.ok(vehicleService.activateOwnVehicle(vehicleId));
    }

    // --- Availability and location -----------------------------------------

    @PatchMapping("/me/availability")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "Go online or offline",
            description = "Only AVAILABLE and OFFLINE are accepted. Going AVAILABLE requires "
                    + "VERIFIED status, an active vehicle, a valid licence and a location on "
                    + "file; otherwise 422 lists every unmet requirement. A BUSY driver cannot "
                    + "go offline mid-ride.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Availability changed"),
            @ApiResponse(responseCode = "409", description = "DRIVER_BUSY: cannot go offline during a ride", content = @Content),
            @ApiResponse(responseCode = "422", description = "DRIVER_NOT_ELIGIBLE, with a 'reasons' list", content = @Content)
    })
    public ResponseEntity<DriverProfileResponse> changeAvailability(
            @Valid @RequestBody AvailabilityRequest request) {
        return ResponseEntity.ok(driverService.changeOwnAvailability(request));
    }

    @PatchMapping("/me/location")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "Report my position",
            description = "Simulated GPS. A position older than 30 minutes is treated as stale "
                    + "and excludes the driver from matching.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Location updated"),
            @ApiResponse(responseCode = "400", description = "Coordinates out of range", content = @Content)
    })
    public ResponseEntity<DriverProfileResponse> updateLocation(@Valid @RequestBody LocationRequest request) {
        return ResponseEntity.ok(driverService.updateOwnLocation(request));
    }

    // --- Public lookup ------------------------------------------------------

    @GetMapping("/{driverId}")
    @Operation(summary = "Get a driver's public summary",
            description = "Any authenticated caller, typically a passenger checking who has "
                    + "been assigned. Licence number and exact coordinates are omitted.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Summary returned"),
            @ApiResponse(responseCode = "404", description = "No such driver", content = @Content)
    })
    public ResponseEntity<DriverSummaryResponse> getSummary(@PathVariable UUID driverId) {
        return ResponseEntity.ok(driverService.getSummary(driverId));
    }
}
