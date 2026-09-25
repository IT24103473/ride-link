package lk.ridelink.ride.client;

import java.math.BigDecimal;
import java.util.UUID;
import lk.ridelink.ride.domain.Location;
import lk.ridelink.ride.domain.VehicleType;
import lk.ridelink.ride.exception.DownstreamUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Calls the Fare &amp; Payment service for a quote (interaction I1).
 *
 * <p>Synchronous, because the passenger needs the price in the same response as the ride
 * they just requested - there is nothing useful to show them until it arrives. That is the
 * defining characteristic of a request/response dependency, and the reason this one is not
 * an event.</p>
 *
 * <p>Note that this call forwards the <em>passenger's own</em> bearer token rather than a
 * service token: the quote belongs to that passenger, and forwarding their identity means
 * the Fare service can record and authorise it as theirs without this service having to be
 * trusted to say who is asking.</p>
 */
@Component
public class FareClient {

    private static final Logger log = LoggerFactory.getLogger(FareClient.class);

    private static final String SERVICE_NAME = "fare-payment";

    private final RestClient fareRestClient;

    public FareClient(RestClient fareRestClient) {
        this.fareRestClient = fareRestClient;
    }

    /**
     * Requests a quote.
     *
     * @param passengerBearerToken the caller's own token, forwarded as-is
     * @throws DownstreamUnavailableException if the service is unreachable, times out, or
     *                                        returns a 5xx
     */
    public FareEstimate estimate(Location pickup, Location destination, VehicleType vehicleType,
                                 String passengerBearerToken) {
        try {
            FareEstimate estimate = fareRestClient.post()
                    .uri("/api/v1/fares/estimates")
                    .header(HttpHeaders.AUTHORIZATION, passengerBearerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new FareEstimateRequest(
                            new LocationPayload(pickup.name(), pickup.lat(), pickup.lng()),
                            new LocationPayload(destination.name(), destination.lat(), destination.lng()),
                            vehicleType.name()))
                    .retrieve()
                    .body(FareEstimate.class);

            if (estimate == null) {
                throw new DownstreamUnavailableException(SERVICE_NAME, "empty estimate response");
            }

            log.debug("Fare estimate {} obtained: {} {}",
                    estimate.id(), estimate.breakdown().total(), estimate.currency());
            return estimate;

        } catch (RestClientResponseException ex) {
            // A 4xx here means this service sent something the Fare service rejected,
            // which is a bug on this side, not the passenger's fault - so it is still
            // reported as a downstream problem rather than blamed on the request.
            log.error("Fare service returned {} for estimate request", ex.getStatusCode(), ex);
            throw new DownstreamUnavailableException(SERVICE_NAME, "HTTP " + ex.getStatusCode().value());

        } catch (DownstreamUnavailableException ex) {
            throw ex;

        } catch (RuntimeException ex) {
            // Connection refused or a timeout: the most common real failure.
            log.error("Fare service unreachable", ex);
            throw new DownstreamUnavailableException(SERVICE_NAME, ex.getClass().getSimpleName());
        }
    }

    // --- Wire types ---------------------------------------------------------
    // Declared locally rather than shared with the Fare service, so neither compiles
    // against the other. Only the fields this service actually uses are mapped.

    record FareEstimateRequest(LocationPayload pickup, LocationPayload destination, String vehicleType) {
    }

    record LocationPayload(String name, Double lat, Double lng) {
    }

    /** The subset of the estimate this service snapshots onto the ride. */
    public record FareEstimate(UUID id, Breakdown breakdown, String currency) {
    }

    public record Breakdown(BigDecimal distanceKm, Integer durationMin, BigDecimal total) {
    }
}
