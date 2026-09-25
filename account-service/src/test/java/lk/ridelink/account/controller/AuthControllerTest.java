package lk.ridelink.account.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import lk.ridelink.account.config.CorrelationIdFilter;
import lk.ridelink.account.config.RideLinkProperties;
import lk.ridelink.account.config.SecurityConfig;
import lk.ridelink.account.domain.AccountStatus;
import lk.ridelink.account.domain.Role;
import lk.ridelink.account.dto.AccountResponse;
import lk.ridelink.account.dto.LoginRequest;
import lk.ridelink.account.dto.LoginResponse;
import lk.ridelink.account.dto.RegisterRequest;
import lk.ridelink.account.exception.AccountNotActiveException;
import lk.ridelink.account.exception.EmailAlreadyRegisteredException;
import lk.ridelink.account.exception.GlobalExceptionHandler;
import lk.ridelink.account.exception.InvalidCredentialsException;
import lk.ridelink.account.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-slice tests for the public auth endpoints: status codes, the shared error body and
 * the validation rules, with the service layer mocked out.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, CorrelationIdFilter.class})
@EnableConfigurationProperties(RideLinkProperties.class)
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    // --- Happy paths --------------------------------------------------------

    @Test
    @DisplayName("POST register/passenger: returns 201 with a Location header")
    void registerPassenger_validRequest_returns201WithLocation() throws Exception {
        UUID id = UUID.randomUUID();
        when(authService.register(any(), eq(Role.PASSENGER))).thenReturn(response(id, Role.PASSENGER));

        mockMvc.perform(post("/api/v1/auth/register/passenger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("Nimal Perera", "nimal@ridelink.test",
                                        "+94771234567", "Passw0rd123"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/accounts/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.role").value("PASSENGER"))
                // The hash must never appear, whatever the service returns.
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("POST register/driver: the role comes from the endpoint, not the request body")
    void registerDriver_validRequest_forcesDriverRole() throws Exception {
        when(authService.register(any(), eq(Role.DRIVER)))
                .thenReturn(response(UUID.randomUUID(), Role.DRIVER));

        mockMvc.perform(post("/api/v1/auth/register/driver")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("Kamal Silva", "kamal@ridelink.test",
                                        "+94771234567", "Passw0rd123"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("DRIVER"));

        // ADMIN is unreachable through any registration endpoint.
        verify(authService).register(any(), eq(Role.DRIVER));
        verify(authService, never()).register(any(), eq(Role.ADMIN));
    }

    @Test
    @DisplayName("POST login: returns the token and echoes the caller's correlation id")
    void login_validCredentials_returns200AndCorrelationId() throws Exception {
        UUID id = UUID.randomUUID();
        when(authService.login(any())).thenReturn(LoginResponse.of("jwt-value", 3600, Role.PASSENGER, id));

        mockMvc.perform(post("/api/v1/auth/login")
                        .header(CorrelationIdFilter.HEADER, "corr-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("nimal@ridelink.test", "Passw0rd123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt-value"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accountId").value(id.toString()))
                // Echoed back so a caller can tie its logs to the server's.
                .andExpect(header().string(CorrelationIdFilter.HEADER, "corr-123"));
    }

    // --- Validation ---------------------------------------------------------

    @Test
    @DisplayName("POST register: an invalid e-mail gives 400 listing the offending field")
    void register_invalidEmail_returns400WithFieldError() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/passenger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("Nimal Perera", "not-an-email",
                                        "+94771234567", "Passw0rd123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/api/v1/auth/register/passenger"));

        verify(authService, never()).register(any(), any());
    }

    @Test
    @DisplayName("POST register: a non-Sri-Lankan phone number is rejected")
    void register_invalidPhoneFormat_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/passenger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("Nimal Perera", "nimal@ridelink.test",
                                        "0771234567", "Passw0rd123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.phone").exists());
    }

    @Test
    @DisplayName("POST register: a password with no digit is rejected by the policy")
    void register_passwordWithoutDigit_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/passenger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("Nimal Perera", "nimal@ridelink.test",
                                        "+94771234567", "PasswordOnly"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    @DisplayName("POST register: a 7-character password is rejected (boundary: minimum is 8)")
    void register_passwordOneCharBelowMinimum_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/passenger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("Nimal Perera", "nimal@ridelink.test",
                                        "+94771234567", "Pass123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    @DisplayName("POST register: an exactly 8-character password is accepted (boundary: minimum is 8)")
    void register_passwordExactlyAtMinimum_isAccepted() throws Exception {
        when(authService.register(any(), eq(Role.PASSENGER)))
                .thenReturn(response(UUID.randomUUID(), Role.PASSENGER));

        mockMvc.perform(post("/api/v1/auth/register/passenger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("Nimal Perera", "nimal@ridelink.test",
                                        "+94771234567", "Pass1234"))))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST register: several bad fields are all reported in one response")
    void register_multipleInvalidFields_reportsAllOfThem() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/passenger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("N", "bad", "123", "short"))))
                .andExpect(status().isBadRequest())
                // Reporting every problem at once saves the client four round trips.
                .andExpect(jsonPath("$.errors.fullName").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.phone").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    @DisplayName("POST login: malformed JSON gives 400 MALFORMED_REQUEST, not a 500")
    void login_malformedJson_returns400MalformedRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"nimal@ridelink.test\", "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    // --- Error mapping ------------------------------------------------------

    @Test
    @DisplayName("POST register: a duplicate e-mail surfaces as 409 EMAIL_ALREADY_REGISTERED")
    void register_duplicateEmail_returns409() throws Exception {
        when(authService.register(any(), eq(Role.PASSENGER)))
                .thenThrow(new EmailAlreadyRegisteredException("nimal@ridelink.test"));

        mockMvc.perform(post("/api/v1/auth/register/passenger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("Nimal Perera", "nimal@ridelink.test",
                                        "+94771234567", "Passw0rd123"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("POST login: bad credentials surface as 401 INVALID_CREDENTIALS")
    void login_invalidCredentials_returns401() throws Exception {
        when(authService.login(any())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("nimal@ridelink.test", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("POST login: a suspended account gives 403, not 401")
    void login_suspendedAccount_returns403() throws Exception {
        when(authService.login(any())).thenThrow(new AccountNotActiveException(AccountStatus.SUSPENDED));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("nimal@ridelink.test", "Passw0rd123"))))
                // 403 rather than 401: the credentials were right, so retrying will not help.
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
    }

    private static AccountResponse response(UUID id, Role role) {
        return new AccountResponse(id, "Nimal Perera", "nimal@ridelink.test", "+94771234567",
                role, AccountStatus.ACTIVE, Instant.now(), Instant.now());
    }
}
