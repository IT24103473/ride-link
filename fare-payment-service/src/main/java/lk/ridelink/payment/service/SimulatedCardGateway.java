package lk.ridelink.payment.service;

import lk.ridelink.payment.domain.FailureReason;
import lk.ridelink.payment.exception.InvalidRequestException;
import org.springframework.stereotype.Component;

/**
 * Stands in for a real card processor.
 *
 * <p>No network call is made and no card data exists: the caller supplies one of three
 * fixed tokens, each of which selects a predetermined outcome. That is what makes the
 * failure paths demonstrable on demand - a real gateway's sandbox cannot be asked to
 * decline a card reliably during a viva.</p>
 *
 * <p>Isolated behind its own class so that swapping in a real provider would mean writing
 * one new implementation, with no change to {@code PaymentService}.</p>
 */
@Component
public class SimulatedCardGateway {

    public static final String TOKEN_SUCCESS = "tok_success";
    public static final String TOKEN_DECLINED = "tok_declined";
    public static final String TOKEN_INSUFFICIENT_FUNDS = "tok_insufficient_funds";

    /**
     * Runs a simulated authorisation.
     *
     * @param cardToken one of the three supported placeholders
     * @return the outcome; {@link Outcome#failureReason()} is null on success
     * @throws InvalidRequestException for an unrecognised token - a 400, because the request
     *         itself is wrong, as distinct from a genuine decline which is a 402
     */
    public Outcome authorise(String cardToken) {
        if (cardToken == null || cardToken.isBlank()) {
            throw new InvalidRequestException("A cardToken is required when paying by CARD");
        }

        return switch (cardToken) {
            case TOKEN_SUCCESS -> Outcome.success();
            case TOKEN_DECLINED -> Outcome.failure(FailureReason.CARD_DECLINED);
            case TOKEN_INSUFFICIENT_FUNDS -> Outcome.failure(FailureReason.INSUFFICIENT_FUNDS);
            // An unknown token is a malformed request, not a declined card: it must not
            // leave a FAILED payment or an attempt record behind.
            default -> throw new InvalidRequestException(
                    "Unknown cardToken. Supported simulated tokens are: "
                            + TOKEN_SUCCESS + ", " + TOKEN_DECLINED + ", " + TOKEN_INSUFFICIENT_FUNDS);
        };
    }

    /** The result of an authorisation attempt. */
    public record Outcome(boolean approved, FailureReason failureReason) {

        static Outcome success() {
            return new Outcome(true, null);
        }

        static Outcome failure(FailureReason reason) {
            return new Outcome(false, reason);
        }
    }
}
