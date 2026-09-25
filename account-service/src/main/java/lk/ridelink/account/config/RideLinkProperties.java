package lk.ridelink.account.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed view of the {@code ridelink.*} configuration block.
 *
 * <p>Binding to a record rather than reading {@code @Value} strings all over the code
 * means every secret and tunable has exactly one declaration, and a missing or
 * malformed value fails at startup instead of at first use.</p>
 *
 * @param security     JWT signing and lifetime settings
 * @param admin        credentials for the account seeded at startup
 * @param serviceClients clientId to secret, for the client-credentials endpoint
 */
@ConfigurationProperties(prefix = "ridelink")
public record RideLinkProperties(Security security, Admin admin, Map<String, String> serviceClients) {

    /**
     * @param jwtSecret HS256 signing key; must be at least 32 bytes. Supplied only via
     *                  the {@code JWT_SECRET} environment variable, never committed.
     */
    public record Security(String jwtSecret, int jwtTtlMinutes, int serviceTokenTtlMinutes) {
    }

    /**
     * Seed admin. An empty password disables seeding, which is what CI and the unit
     * tests rely on.
     */
    public record Admin(String email, String password) {
    }

    public Map<String, String> serviceClients() {
        return serviceClients == null ? Map.of() : serviceClients;
    }
}
