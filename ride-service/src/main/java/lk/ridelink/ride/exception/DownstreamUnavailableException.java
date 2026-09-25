package lk.ridelink.ride.exception;

import org.springframework.http.HttpStatus;

/**
 * A service this one depends on could not be reached, timed out, or returned a 5xx.
 *
 * <p>503 with the service named, so an operator knows immediately which component to look
 * at. The downstream response body is never passed through: it may contain internal
 * details, and it would confuse a client that has no idea another service exists.</p>
 */
public class DownstreamUnavailableException extends ApiException {

    private final String serviceName;

    public DownstreamUnavailableException(String serviceName, String reason) {
        super(HttpStatus.SERVICE_UNAVAILABLE,
                ErrorCodes.DOWNSTREAM_UNAVAILABLE,
                "Downstream service unavailable",
                "The " + serviceName + " service is unavailable (" + reason + "). Please retry shortly.");
        this.serviceName = serviceName;
    }

    public String getServiceName() {
        return serviceName;
    }
}
