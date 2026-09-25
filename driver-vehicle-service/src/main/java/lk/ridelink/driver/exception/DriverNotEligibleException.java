package lk.ridelink.driver.exception;

import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * The driver asked to go online but does not meet every requirement.
 *
 * <p>Carries the full list of unmet requirements rather than just the first one, so the
 * driver is told everything they need to fix in a single response instead of discovering
 * the problems one request at a time.</p>
 */
public class DriverNotEligibleException extends ApiException {

    private final List<String> reasons;

    public DriverNotEligibleException(List<String> reasons) {
        super(HttpStatus.UNPROCESSABLE_ENTITY,
                ErrorCodes.DRIVER_NOT_ELIGIBLE,
                "Driver not eligible to go available",
                "The following requirements are not met: " + String.join("; ", reasons));
        this.reasons = List.copyOf(reasons);
    }

    /** Surfaced as the {@code reasons} property on the error body. */
    public List<String> getReasons() {
        return reasons;
    }
}
