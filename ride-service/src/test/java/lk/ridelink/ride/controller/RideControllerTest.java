package lk.ridelink.ride.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lk.ridelink.ride.config.CorrelationIdFilter;
import lk.ridelink.ride.config.RideLinkProperties;
import lk.ridelink.ride.config.SecurityConfig;
import lk.ridelink.ride.domain.PaymentMethod;
import lk.ridelink.ride.domain.PaymentStatus;
import lk.ridelink.ride.domain.RideStatus;
import lk.ridelink.ride.domain.ServiceArea;
import lk.ridelink.ride.domain.VehicleType;
import lk.ridelink.ride.dto.CancelRideRequest;
import lk.ridelink.ride.dto.CreateRideRequest;
import lk.ridelink.ride.dto.LocationRequest;
import lk.ridelink.ride.dto.LocationResponse;
import lk.ridelink.ride.dto.RideResponse;
import lk.ridelink.ride.exception.ActiveRideExistsException;
import lk.ridelink.ride.exception.DownstreamUnavailableException;
import lk.ridelink.ride.exception.GlobalExceptionHandler;
import lk.ridelink.ride.exception.InvalidRideTransitionException;
import lk.ridelink.ride.exception.NoDriverAvailableException;
import lk.ridelink.ride.security.Role;
import lk.ridelink.ride.service.RideService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Status codes and role checks on the ride endpoints. */
@WebMvcTest(RideController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, CorrelationIdFilter.class})
@EnableConfigurationProperties(RideLinkProperties.class)
@ActiveProfiles("test")
class RideControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RideService rideService;

    @Test
    @DisplayName("requesting a ride without a token gives 401")
    void requestRide_noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/rides")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("a driver cannot request a ride")
    void requestRide_asDriver_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/rides")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest()))
                        .with(caller(Role.DRIVER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verify(rideService, never()).requestRide(any());
    }

    @Test
    @DisplayName("a passenger requesting a ride gets 201 with a Location header")
    void requestRide_asPassenger_returns201() throws Exception {
        UUID rideId = UUID.randomUUID();
        when(rideService.requestRide(any())).thenReturn(rideResponse(rideId, RideStatus.REQUESTED));

        mockMvc.perform(post("/api/v1/rides")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest()))
                        .with(caller(Role.PASSENGER)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/rides/" + rideId))
                .andExpect(jsonPath("$.status").value("REQUESTED"));
    }

    @Test
    @DisplayName("a second active ride gives 409 ACTIVE_RIDE_EXISTS")
    void requestRide_activeRideExists_returns409() throws Exception {
        when(rideService.requestRide(any()))
                .thenThrow(new ActiveRideExistsException(UUID.randomUUID(), RideStatus.ACCEPTED));

        mockMvc.perform(post("/api/v1/rides")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest()))
                        .with(caller(Role.PASSENGER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVE_RIDE_EXISTS"));
    }

    @Test
    @DisplayName("an unreachable downstream service gives 503 DOWNSTREAM_UNAVAILABLE")
    void requestRide_downstreamDown_returns503() throws Exception {
        when(rideService.requestRide(any()))
                .thenThrow(new DownstreamUnavailableException("fare-payment", "timeout"));

        mockMvc.perform(post("/api/v1/rides")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest()))
                        .with(caller(Role.PASSENGER)))
                // 503 rather than 500: it names a component and tells the client to retry.
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DOWNSTREAM_UNAVAILABLE"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("fare-payment")));
    }

    @Test
    @DisplayName("no driver available gives 409, and the ride is still REQUESTED")
    void assign_noDriverAvailable_returns409() throws Exception {
        UUID rideId = UUID.randomUUID();
        when(rideService.assignDriver(rideId))
                .thenThrow(new NoDriverAvailableException("No driver is currently available"));

        mockMvc.perform(post("/api/v1/rides/{id}/assignment", rideId).with(caller(Role.PASSENGER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_DRIVER_AVAILABLE"));
    }

    @Test
    @DisplayName("cancelling a completed ride gives 409 INVALID_RIDE_TRANSITION")
    void cancel_completedRide_returns409() throws Exception {
        UUID rideId = UUID.randomUUID();
        when(rideService.cancel(eq(rideId), any()))
                .thenThrow(new InvalidRideTransitionException(rideId, RideStatus.COMPLETED, RideStatus.CANCELLED));

        mockMvc.perform(post("/api/v1/rides/{id}/cancel", rideId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CancelRideRequest("Too late")))
                        .with(caller(Role.PASSENGER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RIDE_TRANSITION"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("COMPLETED")));
    }

    @Test
    @DisplayName("cancelling without a reason gives 400")
    void cancel_missingReason_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/rides/{id}/cancel", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(caller(Role.PASSENGER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.reason").exists());
    }

    @Test
    @DisplayName("a passenger cannot accept a ride")
    void accept_asPassenger_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/rides/{id}/accept", UUID.randomUUID()).with(caller(Role.PASSENGER)))
                .andExpect(status().isForbidden());

        verify(rideService, never()).accept(any());
    }

    @Test
    @DisplayName("completing with no body is accepted; the estimate is used instead")
    void complete_withoutBody_returns200() throws Exception {
        UUID rideId = UUID.randomUUID();
        when(rideService.complete(eq(rideId), any())).thenReturn(rideResponse(rideId, RideStatus.COMPLETED));

        mockMvc.perform(post("/api/v1/rides/{id}/complete", rideId).with(caller(Role.DRIVER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("a reported distance beyond the allowed range gives 400")
    void complete_distanceOutOfRange_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/rides/{id}/complete", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualDistanceKm\": 5000}")
                        .with(caller(Role.DRIVER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.actualDistanceKm").exists());
    }

    private static RequestPostProcessor caller(Role role) {
        return jwt()
                .jwt(builder -> builder.subject(UUID.randomUUID().toString()).claim("role", role.name()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    private static CreateRideRequest validRequest() {
        return new CreateRideRequest(
                new LocationRequest("Colombo Fort", 6.9344, 79.8428),
                new LocationRequest("Negombo", 7.2083, 79.8358),
                VehicleType.CAR, ServiceArea.NEGOMBO, PaymentMethod.CARD);
    }

    private static RideResponse rideResponse(UUID rideId, RideStatus status) {
        return new RideResponse(rideId, UUID.randomUUID(), null, VehicleType.CAR, ServiceArea.NEGOMBO,
                new LocationResponse("Colombo Fort", 6.9344, 79.8428),
                new LocationResponse("Negombo", 7.2083, 79.8358),
                status, PaymentMethod.CARD, UUID.randomUUID(), new BigDecimal("4595.00"),
                new BigDecimal("39.65"), 96, "LKR", Instant.now(), null, null, null, null, null,
                null, null, PaymentStatus.NOT_DUE);
    }
}
