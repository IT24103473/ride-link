package lk.ridelink.ride.exception;

/** Machine-readable error codes for the Ride Management Service. */
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
    /** The passenger already has a ride that has not finished. */
    public static final String ACTIVE_RIDE_EXISTS = "ACTIVE_RIDE_EXISTS";
    /** Matching found nobody; the ride stays REQUESTED so it can be retried. */
    public static final String NO_DRIVER_AVAILABLE = "NO_DRIVER_AVAILABLE";
    public static final String INVALID_RIDE_TRANSITION = "INVALID_RIDE_TRANSITION";
    public static final String CONCURRENT_MODIFICATION = "CONCURRENT_MODIFICATION";

    // 422
    public static final String BUSINESS_RULE_VIOLATION = "BUSINESS_RULE_VIOLATION";

    // 503
    /** A service this one depends on is unreachable or failing. */
    public static final String DOWNSTREAM_UNAVAILABLE = "DOWNSTREAM_UNAVAILABLE";

    private ErrorCodes() {
    }
}
