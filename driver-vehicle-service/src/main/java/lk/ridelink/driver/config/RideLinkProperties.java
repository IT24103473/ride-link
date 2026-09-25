package lk.ridelink.driver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed view of the {@code ridelink.*} configuration block for this service.
 *
 * @param security JWT verification settings
 * @param matching tunables for the driver-matching rule; kept in configuration rather
 *                 than in Java so the search radius can be changed without a rebuild
 */
@ConfigurationProperties(prefix = "ridelink")
public record RideLinkProperties(Security security, Matching matching) {

    public record Security(String jwtSecret) {
    }

    /**
     * @param defaultRadiusKm          radius used when the caller does not supply one
     * @param maxRadiusKm              hard cap, so one request cannot scan the whole country
     * @param defaultLimit             how many candidates to return by default
     * @param locationFreshnessMinutes a driver whose position is older than this is skipped,
     *                                 because a stale position gives a meaningless distance
     */
    public record Matching(int defaultRadiusKm, int maxRadiusKm, int defaultLimit,
                           int locationFreshnessMinutes) {
    }
}
