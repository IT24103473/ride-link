package lk.ridelink.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lk.ridelink.payment.domain.PaymentMethod;

/**
 * Instruction to settle a payment.
 *
 * <p>The card token is a fixed placeholder that selects a simulated outcome - it is not a
 * card number and nothing resembling card data is ever stored.</p>
 */
@Schema(description = "Settle a payment. CARD requires one of the simulated tokens.")
public record PayRequest(

        @Schema(example = "CARD")
        @NotNull(message = "Payment method is required")
        PaymentMethod method,

        @Schema(description = """
                Required for CARD, ignored for CASH. Simulated outcomes:
                tok_success -> paid; tok_declined -> 402 CARD_DECLINED; \
                tok_insufficient_funds -> 402 INSUFFICIENT_FUNDS. Any other value is a 400.""",
                example = "tok_success")
        String cardToken
) {
}
