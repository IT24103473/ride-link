package lk.ridelink.payment.exception;

/** Machine-readable error codes for the Fare &amp; Payment Service. */
public final class ErrorCodes {

    // 400
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String MALFORMED_REQUEST = "MALFORMED_REQUEST";

    // 401
    public static final String UNAUTHENTICATED = "UNAUTHENTICATED";

    // 402
    /** The simulated card was declined; the payment may be retried. */
    public static final String PAYMENT_DECLINED = "PAYMENT_DECLINED";

    // 403
    public static final String FORBIDDEN = "FORBIDDEN";

    // 404
    public static final String NOT_FOUND = "NOT_FOUND";
    /**
     * The ride exists but its final fare has not been computed yet, because the
     * ride.completed event is still in flight. This is eventual consistency showing
     * through, not an error - clients poll until it turns into a 200.
     */
    public static final String PAYMENT_NOT_READY = "PAYMENT_NOT_READY";

    // 409
    public static final String PAYMENT_ALREADY_COMPLETED = "PAYMENT_ALREADY_COMPLETED";
    public static final String CONCURRENT_MODIFICATION = "CONCURRENT_MODIFICATION";

    // 422
    public static final String FARE_ESTIMATE_EXPIRED = "FARE_ESTIMATE_EXPIRED";
    public static final String BUSINESS_RULE_VIOLATION = "BUSINESS_RULE_VIOLATION";

    private ErrorCodes() {
    }
}
