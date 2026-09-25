package lk.ridelink.account.exception;

/**
 * The machine-readable {@code code} returned in every error body.
 *
 * <p>Clients branch on these, never on the human-readable {@code title} or
 * {@code detail}, which may be reworded at any time. The catalogue is shared across all
 * four services by contract (see CLAUDE.md section 9); each service declares its own
 * copy so that no service depends on another's code.</p>
 */
public final class ErrorCodes {

    // 400
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String MALFORMED_REQUEST = "MALFORMED_REQUEST";

    // 401
    public static final String UNAUTHENTICATED = "UNAUTHENTICATED";
    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";

    // 403
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String ACCOUNT_SUSPENDED = "ACCOUNT_SUSPENDED";
    public static final String ACCOUNT_DEACTIVATED = "ACCOUNT_DEACTIVATED";

    // 404
    public static final String NOT_FOUND = "NOT_FOUND";

    // 409
    public static final String EMAIL_ALREADY_REGISTERED = "EMAIL_ALREADY_REGISTERED";
    public static final String CONCURRENT_MODIFICATION = "CONCURRENT_MODIFICATION";

    // 422
    public static final String BUSINESS_RULE_VIOLATION = "BUSINESS_RULE_VIOLATION";

    private ErrorCodes() {
        // Constants holder.
    }
}
