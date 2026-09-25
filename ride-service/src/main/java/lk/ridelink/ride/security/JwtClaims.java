package lk.ridelink.ride.security;

/**
 * Names of the custom claims RideLink puts in its tokens.
 *
 * <p>Declared as constants because all four services must agree on the exact spelling:
 * a typo here would make a token unreadable to the service that receives it.</p>
 */
public final class JwtClaims {

    /** Authorisation role; mapped to a {@code ROLE_<value>} authority on every service. */
    public static final String ROLE = "role";

    /** Convenience claim so a service can log who acted without calling Account. */
    public static final String EMAIL = "email";

    private JwtClaims() {
    }
}
