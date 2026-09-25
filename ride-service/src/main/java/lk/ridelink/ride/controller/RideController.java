package lk.ridelink.ride.controller;

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
import lk.ridelink.ride.domain.RideStatus;
import lk.ridelink.ride.dto.CancelRideRequest;
import lk.ridelink.ride.dto.CompleteRideRequest;
import lk.ridelink.ride.dto.CreateRideRequest;
import lk.ridelink.ride.dto.RideResponse;
import lk.ridelink.ride.dto.RideStatusHistoryResponse;
import lk.ridelink.ride.service.RideService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
 * The ride lifecycle API.
 *
 * <p>Lifecycle actions are modelled as sub-resources ({@code POST /rides/{id}/accept})
 * rather than as a status field on a PATCH. Each action has its own authorisation rule -
 * only the assigned driver may accept, only the passenger may request assignment - and its
 * own validity window in the state machine, which a single "set status" endpoint would
 * have to untangle with a chain of conditionals.</p>
 */
@RestController
@RequestMapping("/api/v1/rides")
@Tag(name = "Rides", description = "Ride lifecycle from request through to completion")
@SecurityRequirement(name = "bearerAuth")
public class RideController {

    private final RideService rideService;

    public RideController(RideService rideService) {
        this.rideService = rideService;
    }

    @PostMapping
    @PreAuthorize("hasRole('PASSENGER')")
    @Operation(summary = "Request a ride",
            description = """
                    Calls the Fare service for a quote and stores it on the ride, which fixes \
                    the price even if tariffs later change. The ride starts REQUESTED; call \
                    /assignment next to find a driver.

                    A passenger may hold only one unfinished ride at a time.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Ride requested"),
            @ApiResponse(responseCode = "400", description = "Validation failed, or pickup equals destination", content = @Content),
            @ApiResponse(responseCode = "409", description = "ACTIVE_RIDE_EXISTS", content = @Content),
            @ApiResponse(responseCode = "503", description = "DOWNSTREAM_UNAVAILABLE: the fare service is unreachable", content = @Content)
    })
    public ResponseEntity<RideResponse> requestRide(@Valid @RequestBody CreateRideRequest request) {
        // The caller's token is read from the security context inside the service and
        // forwarded to the Fare service, so the quote is recorded against the passenger
        // themselves rather than against this service.
        RideResponse created = rideService.requestRide(request);
        return ResponseEntity.created(URI.create("/api/v1/rides/" + created.id())).body(created);
    }

    @PostMapping("/{rideId}/assignment")
    @Operation(summary = "Find and reserve a driver",
            description = """
                    Asks the Driver service for ranked candidates and attempts to reserve them \
                    in order, skipping any taken in the meantime.

                    A 409 NO_DRIVER_AVAILABLE leaves the ride REQUESTED rather than cancelling \
                    it, so the passenger can simply retry in a moment rather than rebook.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Driver assigned"),
            @ApiResponse(responseCode = "403", description = "Not the passenger or an admin", content = @Content),
            @ApiResponse(responseCode = "404", description = "No such ride", content = @Content),
            @ApiResponse(responseCode = "409", description = "NO_DRIVER_AVAILABLE, or the ride is not REQUESTED", content = @Content),
            @ApiResponse(responseCode = "503", description = "DOWNSTREAM_UNAVAILABLE: the driver service is unreachable", content = @Content)
    })
    public ResponseEntity<RideResponse> assign(@PathVariable UUID rideId) {
        return ResponseEntity.ok(rideService.assignDriver(rideId));
    }

    @PostMapping("/{rideId}/accept")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "Accept an assigned ride",
            description = "ASSIGNED to ACCEPTED. Only the driver actually assigned may accept.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Accepted"),
            @ApiResponse(responseCode = "403", description = "Not the assigned driver", content = @Content),
            @ApiResponse(responseCode = "409", description = "INVALID_RIDE_TRANSITION", content = @Content)
    })
    public ResponseEntity<RideResponse> accept(@PathVariable UUID rideId) {
        return ResponseEntity.ok(rideService.accept(rideId));
    }

    @PostMapping("/{rideId}/reject")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "Reject an assigned ride",
            description = "ASSIGNED back to REQUESTED, releasing the driver. The ride returns "
                    + "to the pool rather than being cancelled, so the passenger can be matched "
                    + "with someone else without rebooking.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rejected; ride is REQUESTED again"),
            @ApiResponse(responseCode = "403", description = "Not the assigned driver", content = @Content),
            @ApiResponse(responseCode = "409", description = "INVALID_RIDE_TRANSITION", content = @Content)
    })
    public ResponseEntity<RideResponse> reject(@PathVariable UUID rideId) {
        return ResponseEntity.ok(rideService.reject(rideId));
    }

    @PostMapping("/{rideId}/start")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "Start the trip",
            description = "ACCEPTED to IN_PROGRESS. From here the ride can no longer be cancelled.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Started"),
            @ApiResponse(responseCode = "403", description = "Not the assigned driver", content = @Content),
            @ApiResponse(responseCode = "409", description = "INVALID_RIDE_TRANSITION", content = @Content)
    })
    public ResponseEntity<RideResponse> start(@PathVariable UUID rideId) {
        return ResponseEntity.ok(rideService.start(rideId));
    }

    @PostMapping("/{rideId}/complete")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "Complete the trip",
            description = """
                    IN_PROGRESS to COMPLETED, publishing ride.completed. That event releases \
                    the driver and triggers the final fare, so this call does not wait on \
                    either service - it cannot fail because Payment is restarting.

                    The body is optional; without a reported distance the estimate is used.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Completed"),
            @ApiResponse(responseCode = "400", description = "Reported distance out of range", content = @Content),
            @ApiResponse(responseCode = "403", description = "Not the assigned driver", content = @Content),
            @ApiResponse(responseCode = "409", description = "INVALID_RIDE_TRANSITION", content = @Content)
    })
    public ResponseEntity<RideResponse> complete(
            @PathVariable UUID rideId,
            @Valid @RequestBody(required = false) CompleteRideRequest request) {
        return ResponseEntity.ok(rideService.complete(rideId, request));
    }

    @PostMapping("/{rideId}/cancel")
    @Operation(summary = "Cancel a ride",
            description = """
                    Allowed from REQUESTED, ASSIGNED or ACCEPTED. An IN_PROGRESS ride cannot \
                    be cancelled - the passenger is already in the vehicle - and COMPLETED and \
                    CANCELLED are terminal, so both give 409.

                    Who cancelled is determined from the caller's relationship to the ride, \
                    not from the body, so a passenger cannot claim it was the driver's doing \
                    to avoid the cancellation fee.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cancelled"),
            @ApiResponse(responseCode = "403", description = "Not involved in this ride", content = @Content),
            @ApiResponse(responseCode = "409", description = "INVALID_RIDE_TRANSITION, e.g. the ride is IN_PROGRESS or already finished", content = @Content)
    })
    public ResponseEntity<RideResponse> cancel(@PathVariable UUID rideId,
                                               @Valid @RequestBody CancelRideRequest request) {
        return ResponseEntity.ok(rideService.cancel(rideId, request));
    }

    @GetMapping("/{rideId}")
    @Operation(summary = "Get a ride",
            description = "Readable by its passenger, its assigned driver, or an admin.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ride returned"),
            @ApiResponse(responseCode = "403", description = "Not involved in this ride", content = @Content),
            @ApiResponse(responseCode = "404", description = "No such ride", content = @Content)
    })
    public ResponseEntity<RideResponse> getById(@PathVariable UUID rideId) {
        return ResponseEntity.ok(rideService.getById(rideId));
    }

    @GetMapping("/{rideId}/history")
    @Operation(summary = "Get a ride's status history",
            description = "Every recorded transition, in order. The ride row holds only the "
                    + "current status, so this is what makes a disputed trip reconstructable.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "History returned"),
            @ApiResponse(responseCode = "403", description = "Not involved in this ride", content = @Content),
            @ApiResponse(responseCode = "404", description = "No such ride", content = @Content)
    })
    public ResponseEntity<List<RideStatusHistoryResponse>> getHistory(@PathVariable UUID rideId) {
        return ResponseEntity.ok(rideService.getHistory(rideId));
    }

    @GetMapping
    @Operation(summary = "List rides",
            description = "Scoped by role: a passenger sees their own rides, a driver the ones "
                    + "assigned to them, an admin all of them. Optionally filtered by status.")
    @ApiResponse(responseCode = "200", description = "Page of rides")
    public ResponseEntity<Page<RideResponse>> list(
            @RequestParam(required = false) RideStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "requestedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(rideService.list(status, pageable));
    }
}
