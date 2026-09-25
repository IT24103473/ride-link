package lk.ridelink.payment.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lk.ridelink.payment.dto.FareEstimateRequest;
import lk.ridelink.payment.dto.FareEstimateResponse;
import lk.ridelink.payment.dto.TariffResponse;
import lk.ridelink.payment.service.FareService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Fare quotes and the published pricing rule. */
@RestController
@RequestMapping("/api/v1/fares")
@Tag(name = "Fares", description = "Fare estimates and the published tariff rule")
@SecurityRequirement(name = "bearerAuth")
public class FareController {

    private final FareService fareService;

    public FareController(FareService fareService) {
        this.fareService = fareService;
    }

    @PostMapping("/estimates")
    @PreAuthorize("hasAnyRole('PASSENGER', 'ADMIN')")
    @Operation(summary = "Quote a fare",
            description = """
                    Prices a journey from the straight-line distance between the two points, \
                    adjusted by the road-winding factor. The quote is stored with an expiry, \
                    so a passenger cannot present an old price after tariffs change.

                    The Ride service calls this when a ride is requested and snapshots the \
                    result onto the ride, which is what fixes the price at booking time.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Quote created"),
            @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content),
            @ApiResponse(responseCode = "403", description = "Caller is a driver", content = @Content)
    })
    public ResponseEntity<FareEstimateResponse> createEstimate(
            @Valid @RequestBody FareEstimateRequest request) {
        FareEstimateResponse created = fareService.createEstimate(request);
        return ResponseEntity.created(URI.create("/api/v1/fares/estimates/" + created.id())).body(created);
    }

    @GetMapping("/estimates/{estimateId}")
    @Operation(summary = "Read a quote back",
            description = "Readable by the passenger who requested it, or an admin. "
                    + "A quote reveals where someone intended to travel, so anyone else gets 403.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Quote returned"),
            @ApiResponse(responseCode = "403", description = "The quote belongs to another passenger", content = @Content),
            @ApiResponse(responseCode = "404", description = "No such quote", content = @Content)
    })
    public ResponseEntity<FareEstimateResponse> getEstimate(@PathVariable UUID estimateId) {
        return ResponseEntity.ok(fareService.getEstimate(estimateId));
    }

    @GetMapping("/tariffs")
    @Operation(summary = "Get the published fare rule",
            description = """
                    Returns the formula and every vehicle class's rates, read from the same \
                    configuration the calculator uses - so what is published here cannot drift \
                    from what is actually charged.

                    Publishing the rule through the API, rather than only in the report, means \
                    a client can always explain a price it was quoted.""")
    @ApiResponse(responseCode = "200", description = "Tariffs returned")
    public ResponseEntity<TariffResponse> getTariffs() {
        return ResponseEntity.ok(fareService.getTariffs());
    }
}
