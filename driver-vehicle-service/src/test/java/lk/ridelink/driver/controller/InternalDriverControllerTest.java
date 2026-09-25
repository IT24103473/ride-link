package lk.ridelink.driver.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lk.ridelink.driver.config.CorrelationIdFilter;
import lk.ridelink.driver.config.RideLinkProperties;
import lk.ridelink.driver.config.SecurityConfig;
import lk.ridelink.driver.domain.ServiceArea;
import lk.ridelink.driver.domain.VehicleType;
import lk.ridelink.driver.dto.DriverCandidateResponse;
import lk.ridelink.driver.dto.ReservationRequest;
import lk.ridelink.driver.exception.DriverNotAvailableException;
import lk.ridelink.driver.exception.GlobalExceptionHandler;
import lk.ridelink.driver.security.Role;
import lk.ridelink.driver.service.DriverMatchingService;
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

/**
 * Access control on the service-to-service endpoints.
 *
 * <p>These are the tests behind the security claim in the report: a passenger holding a
 * perfectly valid token still cannot reserve a driver. Without that boundary, any user
 * could mark the entire fleet BUSY and halt the platform.</p>
 */
@WebMvcTest(InternalDriverController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, CorrelationIdFilter.class})
@EnableConfigurationProperties(RideLinkProperties.class)
@ActiveProfiles("test")
class InternalDriverControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DriverMatchingService matchingService;

    // --- Authentication and authorisation -----------------------------------

    @Test
    @DisplayName("no token: the internal search is 401")
    void findAvailable_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/internal/drivers/available")
                        .param("lat", "7.2083")
                        .param("lng", "79.8358")
                        .param("vehicleType", "CAR")
                        .param("serviceArea", "NEGOMBO"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(matchingService, never()).findAvailable(
                org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(),
                any(), any(), any(), any());
    }

    @Test
    @DisplayName("a passenger token cannot reserve a driver")
    void reserve_passengerToken_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/internal/drivers/{id}/reserve", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReservationRequest(UUID.randomUUID())))
                        .with(caller(Role.PASSENGER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // The refusal happens before any business logic runs.
        verify(matchingService, never()).reserve(any(), any());
    }

    @Test
    @DisplayName("a driver token cannot reserve a driver either")
    void reserve_driverToken_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/internal/drivers/{id}/reserve", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReservationRequest(UUID.randomUUID())))
                        .with(caller(Role.DRIVER)))
                .andExpect(status().isForbidden());

        verify(matchingService, never()).reserve(any(), any());
    }

    @Test
    @DisplayName("an admin token cannot reserve, even though it may search")
    void reserve_adminToken_returns403() throws Exception {
        // Reserving is a machine operation tied to a specific ride; an admin doing it by
        // hand would desynchronise the driver from the Ride service's state.
        mockMvc.perform(post("/api/v1/internal/drivers/{id}/reserve", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReservationRequest(UUID.randomUUID())))
                        .with(caller(Role.ADMIN)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a SERVICE token may search and gets the ranked candidates")
    void findAvailable_serviceToken_returns200() throws Exception {
        when(matchingService.findAvailable(
                org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(),
                eq(VehicleType.CAR), eq(ServiceArea.NEGOMBO), any(), any()))
                .thenReturn(List.of(new DriverCandidateResponse(UUID.randomUUID(), "Kamal Silva",
                        1.84, Instant.now(), VehicleType.CAR, "WP CAB-1234", 12)));

        mockMvc.perform(get("/api/v1/internal/drivers/available")
                        .param("lat", "7.2083")
                        .param("lng", "79.8358")
                        .param("vehicleType", "CAR")
                        .param("serviceArea", "NEGOMBO")
                        .with(caller(Role.SERVICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fullName").value("Kamal Silva"))
                .andExpect(jsonPath("$[0].distanceKm").value(1.84));
    }

    @Test
    @DisplayName("an admin token may search, for fleet troubleshooting")
    void findAvailable_adminToken_returns200() throws Exception {
        when(matchingService.findAvailable(
                org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(),
                any(), any(), any(), any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/internal/drivers/available")
                        .param("lat", "7.2083")
                        .param("lng", "79.8358")
                        .param("vehicleType", "CAR")
                        .param("serviceArea", "NEGOMBO")
                        .with(caller(Role.ADMIN)))
                .andExpect(status().isOk());
    }

    // --- Behaviour ----------------------------------------------------------

    @Test
    @DisplayName("a SERVICE token reserving an available driver gets 204")
    void reserve_serviceToken_returns204() throws Exception {
        mockMvc.perform(post("/api/v1/internal/drivers/{id}/reserve", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReservationRequest(UUID.randomUUID())))
                        .with(caller(Role.SERVICE)))
                .andExpect(status().isNoContent());

        verify(matchingService).reserve(any(), any());
    }

    @Test
    @DisplayName("losing the race surfaces as 409 DRIVER_NOT_AVAILABLE so the caller tries the next candidate")
    void reserve_driverTaken_returns409() throws Exception {
        UUID driverId = UUID.randomUUID();
        doThrow(new DriverNotAvailableException(driverId))
                .when(matchingService).reserve(eq(driverId), any());

        mockMvc.perform(post("/api/v1/internal/drivers/{id}/reserve", driverId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReservationRequest(UUID.randomUUID())))
                        .with(caller(Role.SERVICE)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DRIVER_NOT_AVAILABLE"));
    }

    @Test
    @DisplayName("release returns 204 and is safe to repeat")
    void release_serviceToken_returns204() throws Exception {
        mockMvc.perform(post("/api/v1/internal/drivers/{id}/release", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReservationRequest(UUID.randomUUID())))
                        .with(caller(Role.SERVICE)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("reserve without a ride id is rejected with 400")
    void reserve_missingRideId_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/internal/drivers/{id}/reserve", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(caller(Role.SERVICE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("a latitude outside -90..90 is rejected with 400")
    void findAvailable_latitudeOutOfRange_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/internal/drivers/available")
                        .param("lat", "120.0")
                        .param("lng", "79.8358")
                        .param("vehicleType", "CAR")
                        .param("serviceArea", "NEGOMBO")
                        .with(caller(Role.SERVICE)))
                .andExpect(status().isBadRequest());
    }

    /**
     * A token of the given role. The SERVICE case mirrors what the Account service issues
     * for the client-credentials grant: the subject is a client id, not a UUID.
     */
    private static RequestPostProcessor caller(Role role) {
        String subject = role == Role.SERVICE ? "ride-service" : UUID.randomUUID().toString();
        return jwt()
                .jwt(builder -> builder.subject(subject).claim("role", role.name()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
}
