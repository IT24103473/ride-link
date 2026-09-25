package lk.ridelink.ride.client;

import java.time.Duration;
import java.time.Instant;
import lk.ridelink.ride.config.RideLinkProperties;
import lk.ridelink.ride.exception.DownstreamUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Obtains and caches the SERVICE token this service needs to call the Driver service's
 * internal endpoints (interaction I3).
 *
 * <p>Caching matters: without it, every single assignment would make an extra round trip
 * to the Account service, doubling the latency a passenger waits and making Account a
 * hard dependency of every dispatch. The token is reused until shortly before it expires.</p>
 *
 * <p>The refresh margin is the important detail. Renewing exactly at expiry would
 * routinely produce a token that expires in flight - valid when sent, rejected on arrival -
 * which would look like a random, unreproducible authorisation failure.</p>
 */
@Component
public class ServiceTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(ServiceTokenProvider.class);

    /** Renew this long before expiry, to cover clock skew and time in flight. */
    private static final Duration REFRESH_MARGIN = Duration.ofSeconds(60);

    private static final String SERVICE_NAME = "account";

    private final RestClient accountRestClient;
    private final RideLinkProperties.Clients clients;

    /** Guarded by {@code this}; refreshed rarely, read on every assignment. */
    private volatile CachedToken cached;

    public ServiceTokenProvider(RestClient accountRestClient, RideLinkProperties properties) {
        this.accountRestClient = accountRestClient;
        this.clients = properties.clients();
    }

    /** A currently valid SERVICE token, fetching a new one only when needed. */
    public String token() {
        CachedToken current = cached;
        if (current != null && current.isUsable()) {
            return current.value();
        }

        // Synchronised so a burst of concurrent assignments triggers one fetch, not twenty.
        synchronized (this) {
            if (cached != null && cached.isUsable()) {
                return cached.value();
            }
            cached = fetch();
            return cached.value();
        }
    }

    /** Drops the cached token, so the next call fetches a fresh one. */
    public synchronized void invalidate() {
        cached = null;
    }

    private CachedToken fetch() {
        try {
            ServiceTokenResponse response = accountRestClient.post()
                    .uri("/api/v1/auth/service-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ServiceTokenRequest(clients.clientId(), clients.clientSecret()))
                    .retrieve()
                    .body(ServiceTokenResponse.class);

            if (response == null || response.accessToken() == null) {
                throw new DownstreamUnavailableException(SERVICE_NAME, "empty service-token response");
            }

            Instant expiresAt = Instant.now().plusSeconds(response.expiresIn());
            log.info("Obtained SERVICE token, expires at {}", expiresAt);

            return new CachedToken(response.accessToken(), expiresAt);

        } catch (DownstreamUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            // Covers connection refused, timeouts and any non-2xx from Account. Without a
            // token this service cannot dispatch at all, so the passenger gets a clear 503
            // naming the component at fault rather than an opaque 500.
            log.error("Failed to obtain SERVICE token from Account service", ex);
            throw new DownstreamUnavailableException(SERVICE_NAME, ex.getClass().getSimpleName());
        }
    }

    /** Credentials sent to the client-credentials endpoint. */
    record ServiceTokenRequest(String clientId, String clientSecret) {
    }

    /** What that endpoint returns. */
    record ServiceTokenResponse(String accessToken, String tokenType, long expiresIn) {
    }

    private record CachedToken(String value, Instant expiresAt) {

        boolean isUsable() {
            return Instant.now().isBefore(expiresAt.minus(REFRESH_MARGIN));
        }
    }
}
