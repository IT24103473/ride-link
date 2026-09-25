package lk.ridelink.ride.security;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Reads the authenticated caller out of the security context.
 *
 * <p>Note that {@code sub} is not always a UUID here: a user token carries the account
 * id, but a SERVICE token carries the client id (for example {@code "ride-service"}).
 * {@link #id()} is therefore only valid for human callers, and the internal endpoints
 * use {@link #subject()} instead.</p>
 */
@Component
public class CurrentUser {

    /**
     * The caller's account id.
     *
     * @throws IllegalStateException if called while a SERVICE token is authenticated,
     *                               whose subject is a client id rather than a UUID
     */
    public UUID id() {
        String subject = subject();
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "Subject '" + subject + "' is not an account id; this endpoint is not for service tokens");
        }
    }

    /** Raw {@code sub} claim: an account id for users, a client id for services. */
    public String subject() {
        return jwt().getSubject();
    }

    /**
     * The caller's own token, ready to forward as an {@code Authorization} header.
     *
     * <p>Taken from the validated token in the security context rather than from the raw
     * request header. Two reasons: the value is guaranteed to be the one that actually
     * authenticated this request, and the controllers stay free of header plumbing for
     * something that is really a detail of how one downstream call is made.</p>
     *
     * <p>Used when calling the Fare service, so a quote is recorded against the passenger
     * themselves rather than against this service.</p>
     */
    public String bearerToken() {
        return "Bearer " + jwt().getTokenValue();
    }

    public Role role() {
        return Role.valueOf(jwt().getClaimAsString(JwtClaims.ROLE));
    }

    public boolean isAdmin() {
        return role() == Role.ADMIN;
    }

    public boolean isService() {
        return role() == Role.SERVICE;
    }

    /** True when the caller is the driver in question, for "self or admin" rules. */
    public boolean is(UUID driverId) {
        return id().equals(driverId);
    }

    private Jwt jwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt token)) {
            // Reaching here means an endpoint was left unauthenticated by mistake.
            throw new IllegalStateException("No authenticated JWT in the security context");
        }
        return token;
    }
}
