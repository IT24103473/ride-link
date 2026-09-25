package lk.ridelink.payment.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {

    public NotFoundException(String detail) {
        super(HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND, "Resource not found", detail);
    }

    public NotFoundException(String code, String title, String detail) {
        super(HttpStatus.NOT_FOUND, code, title, detail);
    }

    public static NotFoundException estimate(Object id) {
        return new NotFoundException("No fare estimate exists with id " + id);
    }

    public static NotFoundException payment(Object id) {
        return new NotFoundException("No payment exists with id " + id);
    }

    /**
     * Distinct from a plain 404: the ride may well exist, but its final fare has not been
     * calculated yet. The specific code tells a client to retry rather than give up.
     */
    public static NotFoundException paymentNotReady(Object rideId) {
        return new NotFoundException(ErrorCodes.PAYMENT_NOT_READY, "Payment not ready",
                "No payment has been created for ride " + rideId
                        + " yet; the ride.completed event may still be in flight");
    }

    public static NotFoundException receipt(Object paymentId) {
        return new NotFoundException("Payment " + paymentId + " has no receipt because it is not paid");
    }
}
