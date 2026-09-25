package lk.ridelink.ride.client;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lk.ridelink.ride.domain.Location;
import lk.ridelink.ride.domain.ServiceArea;
import lk.ridelink.ride.domain.VehicleType;
import lk.ridelink.ride.exception.DownstreamUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Calls the Driver &amp; Vehicle service to find and reserve drivers (interaction I2).
 *
 * <p>Synchronous because assignment needs an immediate yes or no: the passenger is waiting
 * to be told whether anyone is coming, and a reservation has to be resolved consistently -
 * exactly one of two simultaneous requests must win. An asynchronous queue would make both
 * "no driver available" and the race outcome slow to report back.</p>
 *
 * <p>These are internal endpoints, so every call carries a SERVICE token from
 * {@link ServiceTokenProvider}, never the passenger's.</p>
 */
@Component
public class DriverClient {

    private static final Logger log = LoggerFactory.getLogger(DriverClient.class);

    private static final String SERVICE_NAME = "driver-vehicle";

    private final RestClient driverRestClient;
    private final ServiceTokenProvider serviceTokenProvider;

    public DriverClient(RestClient driverRestClient, ServiceTokenProvider serviceTokenProvider) {
        this.driverRestClient = driverRestClient;
        this.serviceTokenProvider = serviceTokenProvider;
    }

    /**
     * Finds eligible drivers near the pickup, ranked best-first.
     *
     * <p>The returned order is significant: the caller attempts reservations in exactly
     * this sequence.</p>
     */
    public List<DriverCandidate> findAvailable(Location pickup, VehicleType vehicleType,
                                               ServiceArea serviceArea, int limit) {
        try {
            List<DriverCandidate> candidates = driverRestClient.get()
                    .uri(builder -> builder
                            .path("/api/v1/internal/drivers/available")
                            .queryParam("lat", pickup.lat())
                            .queryParam("lng", pickup.lng())
                            .queryParam("vehicleType", vehicleType.name())
                            .queryParam("serviceArea", serviceArea.name())
                            .queryParam("limit", limit)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, bearer())
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<List<DriverCandidate>>() { });

            return candidates == null ? List.of() : candidates;

        } catch (RestClientResponseException ex) {
            throw translate(ex, "driver search");
        } catch (RuntimeException ex) {
            log.error("Driver service unreachable during search", ex);
            throw new DownstreamUnavailableException(SERVICE_NAME, ex.getClass().getSimpleName());
        }
    }

    /**
     * Attempts to reserve a driver.
     *
     * @return true if reserved; false if the driver was taken first, in which case the
     *         caller should move on to the next candidate
     */
    public boolean reserve(UUID driverId, UUID rideId) {
        try {
            driverRestClient.post()
                    .uri("/api/v1/internal/drivers/{driverId}/reserve", driverId)
                    .header(HttpHeaders.AUTHORIZATION, bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ReservationRequest(rideId))
                    .retrieve()
                    .toBodilessEntity();

            return true;

        } catch (RestClientResponseException ex) {
            // 409 is an expected outcome under contention, not a failure: another ride got
            // there first. Returning false rather than throwing is what lets the caller
            // simply try the next ranked candidate.
            if (ex.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
                log.info("Driver {} was taken before we could reserve them for ride {}", driverId, rideId);
                return false;
            }
            // A driver who has vanished is likewise just an unusable candidate.
            if (ex.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                log.warn("Driver {} no longer exists; skipping", driverId);
                return false;
            }
            throw translate(ex, "driver reservation");

        } catch (RuntimeException ex) {
            log.error("Driver service unreachable during reservation", ex);
            throw new DownstreamUnavailableException(SERVICE_NAME, ex.getClass().getSimpleName());
        }
    }

    /**
     * Releases a driver from a ride.
     *
     * <p>Failures are logged but never propagated. This is always called while completing
     * some other action - a rejection or a cancellation - and that action has already
     * succeeded from the user's point of view. Failing their request because a cleanup
     * call did not land would be worse than a briefly stale driver, and the
     * {@code ride.cancelled} event releases them anyway.</p>
     */
    public void release(UUID driverId, UUID rideId) {
        try {
            driverRestClient.post()
                    .uri("/api/v1/internal/drivers/{driverId}/release", driverId)
                    .header(HttpHeaders.AUTHORIZATION, bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ReservationRequest(rideId))
                    .retrieve()
                    .toBodilessEntity();

        } catch (RuntimeException ex) {
            log.warn("Could not release driver {} from ride {}; the ride event will release them",
                    driverId, rideId, ex);
        }
    }

    private String bearer() {
        return "Bearer " + serviceTokenProvider.token();
    }

    private DownstreamUnavailableException translate(RestClientResponseException ex, String operation) {
        int status = ex.getStatusCode().value();

        // 401/403 means our service token was rejected. Dropping it forces a fresh one on
        // the next attempt, which recovers automatically from a rotated secret or a token
        // that expired in flight.
        if (status == HttpStatus.UNAUTHORIZED.value() || status == HttpStatus.FORBIDDEN.value()) {
            log.error("Service token rejected by the driver service during {}; invalidating it", operation);
            serviceTokenProvider.invalidate();
        } else {
            log.error("Driver service returned {} during {}", status, operation, ex);
        }

        // The downstream body is deliberately not passed through: it may carry internal
        // detail, and it would confuse a client that does not know this service exists.
        return new DownstreamUnavailableException(SERVICE_NAME, "HTTP " + status);
    }

    // --- Wire types ---------------------------------------------------------

    record ReservationRequest(UUID rideId) {
    }

    /** One ranked candidate. Only the fields this service uses are mapped. */
    public record DriverCandidate(UUID driverId, String fullName, double distanceKm,
                                  Instant availableSince, String vehicleType,
                                  String registrationNumber, int completedTrips) {
    }
}
