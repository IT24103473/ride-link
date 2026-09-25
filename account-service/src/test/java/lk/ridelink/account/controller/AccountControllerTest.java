package lk.ridelink.account.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import lk.ridelink.account.dto.ChangeStatusRequest;
import lk.ridelink.account.exception.BusinessRuleException;
import lk.ridelink.account.exception.GlobalExceptionHandler;
import lk.ridelink.account.exception.NotFoundException;
import lk.ridelink.account.service.AccountService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Web-slice tests for the account endpoints, concentrating on authorisation: who may call
 * what, and that a refusal is shaped like every other error.
 */
@WebMvcTest(AccountController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, CorrelationIdFilter.class})
@EnableConfigurationProperties(RideLinkProperties.class)
@ActiveProfiles("test")
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AccountService accountService;

    // --- Authentication -----------------------------------------------------

    @Test
    @DisplayName("GET /accounts/me without a token gives 401 in the shared error shape")
    void getOwnProfile_noToken_returns401ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/me"))
                .andExpect(status().isUnauthorized())
                // Spring Security's default 401 has an empty body; the custom entry point
                // is what makes it match every other error in the system.
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(accountService, never()).getOwnProfile();
    }

    @Test
    @DisplayName("GET /accounts/me with a valid token returns the caller's profile")
    void getOwnProfile_authenticated_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(accountService.getOwnProfile()).thenReturn(response(id, Role.PASSENGER));

        mockMvc.perform(get("/api/v1/accounts/me").with(user(id, Role.PASSENGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    // --- Role-based authorisation ------------------------------------------

    @Test
    @DisplayName("GET /accounts as a passenger gives 403: the listing is admin-only")
    void search_callerIsPassenger_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/accounts").with(user(UUID.randomUUID(), Role.PASSENGER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verify(accountService, never()).search(any(), any(), any());
    }

    @Test
    @DisplayName("GET /accounts as an admin returns a page and passes the filters through")
    void search_callerIsAdmin_returns200AndAppliesFilters() throws Exception {
        Page<AccountResponse> page = new PageImpl<>(
                java.util.List.of(response(UUID.randomUUID(), Role.DRIVER)));
        when(accountService.search(eq(Role.DRIVER), eq(AccountStatus.ACTIVE), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/accounts")
                        .param("role", "DRIVER")
                        .param("status", "ACTIVE")
                        .with(user(UUID.randomUUID(), Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].role").value("DRIVER"));

        verify(accountService).search(eq(Role.DRIVER), eq(AccountStatus.ACTIVE), any());
    }

    @Test
    @DisplayName("PATCH /accounts/{id}/status as a driver gives 403")
    void changeStatus_callerIsDriver_returns403() throws Exception {
        mockMvc.perform(patch("/api/v1/accounts/{id}/status", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ChangeStatusRequest(AccountStatus.SUSPENDED, "abuse")))
                        .with(user(UUID.randomUUID(), Role.DRIVER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verify(accountService, never()).changeStatus(any(), any());
    }

    @Test
    @DisplayName("PATCH /accounts/{id}/status as an admin succeeds")
    void changeStatus_callerIsAdmin_returns200() throws Exception {
        UUID target = UUID.randomUUID();
        AccountResponse suspended = new AccountResponse(target, "Kamal Silva", "kamal@ridelink.test",
                "+94771234567", Role.DRIVER, AccountStatus.SUSPENDED, Instant.now(), Instant.now());
        when(accountService.changeStatus(eq(target), any())).thenReturn(suspended);

        mockMvc.perform(patch("/api/v1/accounts/{id}/status", target)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ChangeStatusRequest(AccountStatus.SUSPENDED, "abuse")))
                        .with(user(UUID.randomUUID(), Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    @DisplayName("PATCH /accounts/{id}/status: an admin targeting themselves gives 422")
    void changeStatus_adminTargetsSelf_returns422() throws Exception {
        UUID adminId = UUID.randomUUID();
        when(accountService.changeStatus(eq(adminId), any()))
                .thenThrow(new BusinessRuleException("An admin cannot change their own account status"));

        mockMvc.perform(patch("/api/v1/accounts/{id}/status", adminId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ChangeStatusRequest(AccountStatus.SUSPENDED, "oops")))
                        .with(user(adminId, Role.ADMIN)))
                // 422, not 400: the body was perfectly valid, the action was not allowed.
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("PATCH /accounts/{id}/status: a status outside the enum gives 400, not 500")
    void changeStatus_unknownStatusValue_returns400() throws Exception {
        mockMvc.perform(patch("/api/v1/accounts/{id}/status", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BANNED\",\"reason\":\"x\"}")
                        .with(user(UUID.randomUUID(), Role.ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    // --- Ownership ----------------------------------------------------------

    @Test
    @DisplayName("GET /accounts/{id}: reading someone else's account as a passenger gives 403")
    void getById_notOwnerNotAdmin_returns403() throws Exception {
        UUID target = UUID.randomUUID();
        when(accountService.getById(target))
                .thenThrow(new AccessDeniedException("Only an admin or the account holder may read this account"));

        mockMvc.perform(get("/api/v1/accounts/{id}", target).with(user(UUID.randomUUID(), Role.PASSENGER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("GET /accounts/{id}: an unknown id gives 404 NOT_FOUND")
    void getById_unknownId_returns404() throws Exception {
        UUID unknown = UUID.randomUUID();
        when(accountService.getById(unknown)).thenThrow(NotFoundException.account(unknown));

        mockMvc.perform(get("/api/v1/accounts/{id}", unknown).with(user(UUID.randomUUID(), Role.ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /accounts/{id}: a non-UUID path variable gives 400, not 500")
    void getById_malformedUuid_returns400() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/accounts/not-a-uuid")
                        .with(user(UUID.randomUUID(), Role.ADMIN)))
                .andExpect(status().isBadRequest());
    }

    // --- Helpers ------------------------------------------------------------

    /**
     * Builds an authenticated request the same way the real filter chain would: a JWT
     * with a {@code sub} and a {@code role} claim, plus the matching ROLE_ authority that
     * {@code SecurityConfig}'s converter would derive from it.
     */
    private static RequestPostProcessor user(UUID accountId, Role role) {
        return jwt()
                .jwt(builder -> builder
                        .subject(accountId.toString())
                        .claim("role", role.name())
                        .claim("email", "user@ridelink.test"))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    private static AccountResponse response(UUID id, Role role) {
        return new AccountResponse(id, "Nimal Perera", "nimal@ridelink.test", "+94771234567",
                role, AccountStatus.ACTIVE, Instant.now(), Instant.now());
    }
}
