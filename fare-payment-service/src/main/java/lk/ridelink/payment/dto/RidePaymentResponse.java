package lk.ridelink.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What a ride cost and how it stands, in one response.
 *
 * <p>Combined so a client polling after ride completion gets the fare breakdown and the
 * payment state together, rather than having to make a second call once the first
 * succeeds.</p>
 */
@Schema(description = "Final fare and payment status for a ride")
public record RidePaymentResponse(
        FinalFareResponse finalFare,
        PaymentResponse payment
) {
}
