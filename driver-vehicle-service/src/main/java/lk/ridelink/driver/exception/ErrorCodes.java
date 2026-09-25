package lk.ridelink.driver.exception;

/**
 * Machine-readable error codes for the Driver &amp; Vehicle Service.
 *
 * <p>The shared catalogue in CLAUDE.md section 9 is the contract; each service declares
 * its own copy so no service compiles against another's code.</p>
 */
public final class ErrorCodes {

    // 400
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String MALFORMED_REQUEST = "MALFORMED_REQUEST";

    // 401
    public static final String UNAUTHENTICATED = "UNAUTHENTICATED";

    // 403
    public static final String FORBIDDEN = "FORBIDDEN";

    // 404
    public static final String NOT_FOUND = "NOT_FOUND";

    // 409
    /** Reserve lost the race: the driver is no longer AVAILABLE. */
    public static final String DRIVER_NOT_AVAILABLE = "DRIVER_NOT_AVAILABLE";
    /** A BUSY driver tried to go OFFLINE mid-ride. */
    public static final String DRIVER_BUSY = "DRIVER_BUSY";
    public static final String CONCURRENT_MODIFICATION = "CONCURRENT_MODIFICATION";
    public static final String VEHICLE_ALREADY_REGISTERED = "VEHICLE_ALREADY_REGISTERED";

    // 422
    /** Returned with the list of unmet requirements, so the driver knows what to fix. */
    public static final String DRIVER_NOT_ELIGIBLE = "DRIVER_NOT_ELIGIBLE";
    public static final String BUSINESS_RULE_VIOLATION = "BUSINESS_RULE_VIOLATION";

    private ErrorCodes() {
    }
}
