package lk.ridelink.account.security;

import java.util.UUID;
import lk.ridelink.account.domain.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Reads the authenticated caller out of the security context.
 *
 * <p>Ownership checks ("is this the ride's passenger?") live in the service layer and
 * need the caller's id, not just their role. Pulling the {@code sub} claim through one
 * class keeps that parsing in a single place and out of every service method.</p>
 */
@Component
public class CurrentUser {

    /** The caller's account id, taken from the token's {@code sub} claim. */
    public UUID id() {
        String subject = jwt().getSubject();
        return UUID.fromString(subject);
    }

    public Role role() {
        return Role.valueOf(jwt().getClaimAsString(JwtClaims.ROLE));
    }

    public boolean isAdmin() {
        return role() == Role.ADMIN;
    }

    /** True when the caller is the account in question, used for "self or admin" rules. */
    public boolean is(UUID accountId) {
        return id().equals(accountId);
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
