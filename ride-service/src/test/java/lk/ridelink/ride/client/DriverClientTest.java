package lk.ridelink.ride.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lk.ridelink.ride.domain.Location;
import lk.ridelink.ride.domain.ServiceArea;
import lk.ridelink.ride.domain.VehicleType;
import lk.ridelink.ride.exception.DownstreamUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.web.client.RestClient;

/**
 * How the driver client behaves when the Driver service answers badly.
 *
 * <p>Uses {@link MockRestServiceServer} rather than a mocked client, so the real HTTP
 * handling, deserialisation and status-code branching are all exercised. Those branches
 * are precisely what decides whether a passenger sees "try again", "no driver available",
 * or a 503 - and they cannot be checked by mocking the class that contains them.</p>
 */
@ExtendWith(MockitoExtension.class)
class DriverClientTest {

    private static final Location PICKUP = new Location("Negombo", 7.2083, 79.8358);

    @Mock
    private ServiceTokenProvider serviceTokenProvider;

    private MockRestServiceServer server;
    private DriverClient driverClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://driver");
        server = MockRestServiceServer.bindTo(builder).build();
        driverClient = new DriverClient(builder.build(), serviceTokenProvider);
    }

    // --- Search -------------------------------------------------------------

    @Test
    @DisplayName("a successful search deserialises the ranked candidates")
    void findAvailable_success_returnsCandidates() {
        when(serviceTokenProvider.token()).thenReturn("svc-token");

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/api/v1/internal/drivers/available")))
                .andExpect(method(HttpMethod.GET))
                // Internal endpoints require a SERVICE token, never the passenger's.
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer svc-token"))
                .andRespond(withSuccess("""
                        [{"driverId":"11111111-1111-1111-1111-111111111111",
                          "fullName":"Kamal Silva","distanceKm":1.84,
                          "availableSince":"2026-09-28T09:00:00Z","vehicleType":"CAR",
                          "registrationNumber":"WP CAB-1234","completedTrips":12}]
                        """, MediaType.APPLICATION_JSON));

        List<DriverClient.DriverCandidate> candidates = driverClient.findAvailable(
                PICKUP, VehicleType.CAR, ServiceArea.NEGOMBO, 3);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).fullName()).isEqualTo("Kamal Silva");
        assertThat(candidates.get(0).distanceKm()).isEqualTo(1.84);
        server.verify();
    }

    @Test
    @DisplayName("an empty result is an empty list, not an error")
    void findAvailable_noDrivers_returnsEmptyList() {
        when(serviceTokenProvider.token()).thenReturn("svc-token");
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/available")))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        // "Nobody nearby" is a normal answer; the service layer turns it into a 409.
        assertThat(driverClient.findAvailable(PICKUP, VehicleType.VAN, ServiceArea.GALLE, 3)).isEmpty();
    }

    @Test
    @DisplayName("a 500 from the driver service becomes a 503 naming that service")
    void findAvailable_serverError_throwsDownstreamUnavailable() {
        when(serviceTokenProvider.token()).thenReturn("svc-token");
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/available")))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> driverClient.findAvailable(PICKUP, VehicleType.CAR, ServiceArea.NEGOMBO, 3))
                .isInstanceOf(DownstreamUnavailableException.class)
                .extracting(ex -> ((DownstreamUnavailableException) ex).getServiceName())
                .isEqualTo("driver-vehicle");
    }

    @Test
    @DisplayName("a connection failure becomes a 503 rather than an opaque 500")
    void findAvailable_connectionRefused_throwsDownstreamUnavailable() {
        when(serviceTokenProvider.token()).thenReturn("svc-token");
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/available")))
                .andRespond(withException(new IOException("Connection refused")));

        // The most common real failure: the other service simply is not running.
        assertThatThrownBy(() -> driverClient.findAvailable(PICKUP, VehicleType.CAR, ServiceArea.NEGOMBO, 3))
                .isInstanceOf(DownstreamUnavailableException.class);
    }

    @Test
    @DisplayName("a rejected service token is dropped so the next call fetches a fresh one")
    void findAvailable_tokenRejected_invalidatesCachedToken() {
        when(serviceTokenProvider.token()).thenReturn("stale-token");
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/available")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> driverClient.findAvailable(PICKUP, VehicleType.CAR, ServiceArea.NEGOMBO, 3))
                .isInstanceOf(DownstreamUnavailableException.class);

        // Recovers automatically from a rotated secret or a token that expired in flight,
        // instead of failing every dispatch until a restart.
        verify(serviceTokenProvider).invalidate();
    }

    // --- Reserve ------------------------------------------------------------

    @Test
    @DisplayName("a 204 from reserve means the driver is ours")
    void reserve_success_returnsTrue() {
        when(serviceTokenProvider.token()).thenReturn("svc-token");
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/reserve")))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        assertThat(driverClient.reserve(UUID.randomUUID(), UUID.randomUUID())).isTrue();
    }

    @Test
    @DisplayName("a 409 means the driver was taken, and is reported as false rather than thrown")
    void reserve_conflict_returnsFalseWithoutThrowing() {
        when(serviceTokenProvider.token()).thenReturn("svc-token");
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/reserve")))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        // This is the crux of the retry loop: losing a race is an expected outcome under
        // load, so it must not surface as an exception the caller has to catch.
        assertThat(driverClient.reserve(UUID.randomUUID(), UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("a 404 means the driver has vanished, which is likewise just an unusable candidate")
    void reserve_notFound_returnsFalse() {
        when(serviceTokenProvider.token()).thenReturn("svc-token");
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/reserve")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(driverClient.reserve(UUID.randomUUID(), UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("a 500 during reserve is a genuine outage and does throw")
    void reserve_serverError_throwsDownstreamUnavailable() {
        when(serviceTokenProvider.token()).thenReturn("svc-token");
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/reserve")))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        // Unlike a 409, this must not be mistaken for "driver taken" - that would report
        // an outage to the passenger as "no drivers available".
        assertThatThrownBy(() -> driverClient.reserve(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(DownstreamUnavailableException.class);
    }

    // --- Release ------------------------------------------------------------

    @Test
    @DisplayName("a failed release is swallowed, because the user's action already succeeded")
    void release_failure_doesNotThrow() {
        when(serviceTokenProvider.token()).thenReturn("svc-token");
        server.expect(ExpectedCount.once(), requestTo(org.hamcrest.Matchers.containsString("/release")))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        // Failing a driver's rejection because a cleanup call did not land would be worse
        // than a briefly stale driver - and the ride event releases them anyway.
        driverClient.release(UUID.randomUUID(), UUID.randomUUID());

        server.verify();
    }
}
