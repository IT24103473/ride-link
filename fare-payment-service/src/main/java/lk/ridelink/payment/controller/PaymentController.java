package lk.ridelink.payment.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lk.ridelink.payment.dto.PayRequest;
import lk.ridelink.payment.dto.PaymentResponse;
import lk.ridelink.payment.dto.ReceiptResponse;
import lk.ridelink.payment.dto.RidePaymentResponse;
import lk.ridelink.payment.service.PaymentService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Settling charges and reading receipts. */
@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments", description = "Final fares, simulated payment and receipts")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/rides/{rideId}")
    @Operation(summary = "Get what a ride cost and whether it is paid",
            description = """
                    Returns the final fare and the payment together.

                    Expect 404 PAYMENT_NOT_READY immediately after a ride completes: the fare \
                    is computed from the ride.completed event, which takes a moment to arrive. \
                    That is eventual consistency showing through, not a failure - poll until \
                    it turns into a 200. The distinct code exists so a client can tell \
                    "not yet" apart from "no such thing".

                    A ride cancelled without a fee has no payment at all, and stays 404.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fare and payment returned"),
            @ApiResponse(responseCode = "403", description = "You were not involved in this ride", content = @Content),
            @ApiResponse(responseCode = "404", description = "PAYMENT_NOT_READY, or no charge is due", content = @Content)
    })
    public ResponseEntity<RidePaymentResponse> getForRide(@PathVariable UUID rideId) {
        return ResponseEntity.ok(paymentService.getForRide(rideId));
    }

    @PostMapping("/{paymentId}/pay")
    @Operation(summary = "Pay for a ride",
            description = """
                    CASH succeeds immediately, since the money changed hands in the vehicle and \
                    this service only records it.

                    CARD runs the simulated gateway. No card data is stored or accepted: \
                    cardToken is one of three fixed placeholders that select an outcome - \
                    tok_success, tok_declined, or tok_insufficient_funds. Any other value is a \
                    400, because that is a malformed request rather than a declined card, and \
                    must not leave a failed payment behind.

                    A declined payment stays FAILED and can be retried; each try is recorded as \
                    a separate attempt, which is what makes a disputed charge explainable.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paid"),
            @ApiResponse(responseCode = "400", description = "Unknown card token", content = @Content),
            @ApiResponse(responseCode = "402", description = "PAYMENT_DECLINED; may be retried", content = @Content),
            @ApiResponse(responseCode = "403", description = "Only the ride's passenger may pay", content = @Content),
            @ApiResponse(responseCode = "404", description = "No such payment", content = @Content),
            @ApiResponse(responseCode = "409", description = "PAYMENT_ALREADY_COMPLETED", content = @Content)
    })
    public ResponseEntity<PaymentResponse> pay(@PathVariable UUID paymentId,
                                               @Valid @RequestBody PayRequest request) {
        return ResponseEntity.ok(paymentService.pay(paymentId, request));
    }

    @GetMapping("/{paymentId}/receipt")
    @Operation(summary = "Get the receipt for a paid ride",
            description = "Available only once the payment has succeeded, so a receipt can "
                    + "never be produced for an unpaid or failed charge. Receipt numbers are "
                    + "gapless and unique, in the form RL-2026-000123.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Receipt returned"),
            @ApiResponse(responseCode = "403", description = "You were not involved in this ride", content = @Content),
            @ApiResponse(responseCode = "404", description = "No such payment, or it is not paid", content = @Content)
    })
    public ResponseEntity<ReceiptResponse> getReceipt(@PathVariable UUID paymentId) {
        return ResponseEntity.ok(paymentService.getReceipt(paymentId));
    }

    @GetMapping
    @Operation(summary = "List payments",
            description = "A passenger sees only their own; an admin sees all. Paginated.")
    @ApiResponse(responseCode = "200", description = "Page of payments")
    public ResponseEntity<Page<PaymentResponse>> list(
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(paymentService.list(pageable));
    }
}
