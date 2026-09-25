package lk.ridelink.ride.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed view of the {@code ridelink.*} configuration block.
 *
 * <p>Downstream URLs are configuration, never constants in code: the services must be
 * independently deployable, which means this one cannot assume where the others live.</p>
 */
@ConfigurationProperties(prefix = "ridelink")
public record RideLinkProperties(Security security, Clients clients, Assignment assignment) {

    public record Security(String jwtSecret) {
    }

    /**
     * @param connectTimeoutMs how long to wait for a connection; short, because an
     *                         unreachable service should fail fast rather than hold a
     *                         passenger's request open
     * @param readTimeoutMs    how long to wait for a response
     * @param clientSecret     credentials this service presents to Account for a SERVICE token
     */
    public record Clients(String accountUrl, String driverUrl, String fareUrl,
                          int connectTimeoutMs, int readTimeoutMs,
                          String clientId, String clientSecret) {
    }

    /**
     * @param maxCandidates how many ranked drivers to attempt a reservation on before
     *                      giving up. More than one because a driver can be taken between
     *                      the search and the reservation; bounded, because a passenger
     *                      should not wait while the whole city is tried.
     */
    public record Assignment(int maxCandidates) {
    }
}
