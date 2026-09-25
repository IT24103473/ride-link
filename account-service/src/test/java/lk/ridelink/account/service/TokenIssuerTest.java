package lk.ridelink.account.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;
import lk.ridelink.account.config.RideLinkProperties;
import lk.ridelink.account.domain.Account;
import lk.ridelink.account.domain.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Verifies the exact claims RideLink tokens carry.
 *
 * <p>These assertions matter beyond this service: the other three parse {@code sub} as
 * the account id and map {@code role} to a Spring authority. A change here that went
 * unnoticed would break authorisation everywhere at once, so the contract is pinned by
 * a test rather than by convention.</p>
 *
 * <p>A real encoder and decoder are used - not mocks - so the test also proves a token
 * this service signs is one a resource server can actually verify.</p>
 */
class TokenIssuerTest {

    /** Test-only key. Must be at least 32 bytes for HS256. */
    private static final String SECRET = "test-only-secret-key-not-a-real-credential-32b";

    private TokenIssuer tokenIssuer;
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        SecretKeySpec key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

        NimbusJwtEncoder encoder = new NimbusJwtEncoder(
                new ImmutableJWKSet<>(new JWKSet(new OctetSequenceKey.Builder(key).build())));
        jwtDecoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();

        RideLinkProperties properties = new RideLinkProperties(
                new RideLinkProperties.Security(SECRET, 60, 10),
                new RideLinkProperties.Admin("admin@ridelink.test", ""),
                Map.of());

        tokenIssuer = new TokenIssuer(encoder, properties);
    }

    @Test
    @DisplayName("user token: sub is the account id and role/email claims match the account")
    void issueForAccount_activeAccount_setsExpectedClaims() {
        Account account = Account.create("Kamal Silva", "kamal@ridelink.test", "+94771234567",
                "$2a$10$hashed", Role.DRIVER);

        TokenIssuer.IssuedToken issued = tokenIssuer.issueForAccount(account);
        Jwt decoded = jwtDecoder.decode(issued.value());

        // sub is what every other service stores as driverId / passengerId.
        assertThat(decoded.getSubject()).isEqualTo(account.getId().toString());
        assertThat(decoded.getClaimAsString("role")).isEqualTo("DRIVER");
        assertThat(decoded.getClaimAsString("email")).isEqualTo("kamal@ridelink.test");
        // Read as a raw claim: "iss" is a StringOrURI per the JWT spec, and our issuer is
        // a plain name, which Jwt#getIssuer would try (and fail) to parse as a URL.
        assertThat(decoded.getClaimAsString("iss")).isEqualTo("ridelink-account-service");
    }

    @Test
    @DisplayName("user token: expiry honours the configured 60 minute TTL")
    void issueForAccount_usesConfiguredTtl() {
        Account account = Account.create("Nimal Perera", "nimal@ridelink.test", "+94771234567",
                "$2a$10$hashed", Role.PASSENGER);

        TokenIssuer.IssuedToken issued = tokenIssuer.issueForAccount(account);
        Jwt decoded = jwtDecoder.decode(issued.value());

        assertThat(issued.expiresInSeconds()).isEqualTo(3600);
        assertThat(decoded.getExpiresAt()).isNotNull();
        assertThat(decoded.getIssuedAt()).isNotNull();
        // Allow a second of slack for clock movement during the test.
        assertThat(java.time.Duration.between(decoded.getIssuedAt(), decoded.getExpiresAt()).toSeconds())
                .isBetween(3599L, 3601L);
    }

    @Test
    @DisplayName("user token: password hash never appears in the token")
    void issueForAccount_doesNotLeakPasswordHash() {
        Account account = Account.create("Nimal Perera", "nimal@ridelink.test", "+94771234567",
                "$2a$10$verysecrethash", Role.PASSENGER);

        Jwt decoded = jwtDecoder.decode(tokenIssuer.issueForAccount(account).value());

        // A JWT is signed, not encrypted: anyone holding it can read every claim.
        assertThat(decoded.getClaims().values().toString()).doesNotContain("verysecrethash");
    }

    @Test
    @DisplayName("service token: role is SERVICE, sub is the client id, and the TTL is short")
    void issueForServiceClient_setsServiceRoleAndShortTtl() {
        TokenIssuer.IssuedToken issued = tokenIssuer.issueForServiceClient("ride-service");
        Jwt decoded = jwtDecoder.decode(issued.value());

        assertThat(decoded.getSubject()).isEqualTo("ride-service");
        assertThat(decoded.getClaimAsString("role")).isEqualTo("SERVICE");
        // 10 minutes, deliberately far shorter than a user token: a leaked service token
        // can reserve drivers across the whole platform.
        assertThat(issued.expiresInSeconds()).isEqualTo(600);
        assertThat(issued.expiresAt()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("service token: carries no email claim, because it belongs to no person")
    void issueForServiceClient_hasNoEmailClaim() {
        Jwt decoded = jwtDecoder.decode(tokenIssuer.issueForServiceClient("ride-service").value());

        assertThat(decoded.getClaimAsString("email")).isNull();
    }
}
