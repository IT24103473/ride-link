package lk.ridelink.account.service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lk.ridelink.account.config.RideLinkProperties;
import lk.ridelink.account.domain.Account;
import lk.ridelink.account.domain.Role;
import lk.ridelink.account.security.JwtClaims;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Mints the HS256 JWTs that every RideLink service accepts.
 *
 * <p>This is the <em>only</em> class in the system that signs a token. Concentrating it
 * here means the claim set cannot drift between the login and service-token paths, and
 * the other three services need no signing key logic at all - they only verify.</p>
 *
 * <p>Known limitation, stated in the report: HS256 is symmetric, so every service holds
 * the same secret and could in principle mint tokens itself. The improvement is RS256
 * with Account publishing a JWKS endpoint, leaving the others with only a public key.</p>
 */
@Component
public class TokenIssuer {

    /** Identifies who issued a token; checked by nothing today, useful for debugging. */
    private static final String ISSUER = "ridelink-account-service";

    private final JwtEncoder jwtEncoder;
    private final Duration userTokenTtl;
    private final Duration serviceTokenTtl;

    public TokenIssuer(JwtEncoder jwtEncoder, RideLinkProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.userTokenTtl = Duration.ofMinutes(properties.security().jwtTtlMinutes());
        this.serviceTokenTtl = Duration.ofMinutes(properties.security().serviceTokenTtlMinutes());
    }

    /**
     * Token for a human user. {@code sub} is the account id, which is the stable
     * identifier the other services store against rides, drivers and payments.
     */
    public IssuedToken issueForAccount(Account account) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(userTokenTtl);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(account.getId().toString())
                .claim(JwtClaims.ROLE, account.getRole().name())
                .claim(JwtClaims.EMAIL, account.getEmail())
                .build();

        return new IssuedToken(encode(claims), userTokenTtl.toSeconds(), expiresAt);
    }

    /**
     * Token for a calling service. {@code sub} is the client id (e.g. "ride-service")
     * rather than a UUID, and the role is {@link Role#SERVICE}, which is the only role
     * the internal endpoints accept.
     *
     * <p>The TTL is deliberately short: a leaked service token is far more dangerous
     * than a user token, and services re-request one transparently.</p>
     */
    public IssuedToken issueForServiceClient(String clientId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(serviceTokenTtl);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(clientId)
                .claim(JwtClaims.ROLE, Role.SERVICE.name())
                .build();

        return new IssuedToken(encode(claims), serviceTokenTtl.toSeconds(), expiresAt);
    }

    private String encode(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /**
     * A signed token and its lifetime.
     *
     * @param value            the compact JWT
     * @param expiresInSeconds lifetime, returned to the client so it can refresh in time
     * @param expiresAt        absolute expiry, used by callers that cache tokens
     */
    public record IssuedToken(String value, long expiresInSeconds, Instant expiresAt) {
    }

    /** Convenience for tests and callers that only need the subject as a UUID. */
    public static UUID subjectAsUuid(String subject) {
        return UUID.fromString(subject);
    }
}
