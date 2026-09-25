package lk.ridelink.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lk.ridelink.account.config.RideLinkProperties;
import lk.ridelink.account.domain.Account;
import lk.ridelink.account.domain.AccountStatus;
import lk.ridelink.account.domain.Role;
import lk.ridelink.account.dto.AccountResponse;
import lk.ridelink.account.dto.LoginRequest;
import lk.ridelink.account.dto.LoginResponse;
import lk.ridelink.account.dto.RegisterRequest;
import lk.ridelink.account.dto.ServiceTokenRequest;
import lk.ridelink.account.exception.AccountNotActiveException;
import lk.ridelink.account.exception.EmailAlreadyRegisteredException;
import lk.ridelink.account.exception.ErrorCodes;
import lk.ridelink.account.exception.InvalidCredentialsException;
import lk.ridelink.account.mapper.AccountMapper;
import lk.ridelink.account.messaging.DriverRegisteredEvent;
import lk.ridelink.account.messaging.EventPublisher;
import lk.ridelink.account.messaging.EventTypes;
import lk.ridelink.account.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Unit tests for registration and token issuing. Everything is mocked, so these run in
 * milliseconds and assert business behaviour rather than wiring.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String RAW_PASSWORD = "Passw0rd123";
    private static final String HASH = "$2a$10$hashed";

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TokenIssuer tokenIssuer;
    @Mock
    private EventPublisher eventPublisher;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        RideLinkProperties properties = new RideLinkProperties(
                new RideLinkProperties.Security("secret", 60, 10),
                new RideLinkProperties.Admin("admin@ridelink.test", ""),
                Map.of("ride-service", "correct-secret"));

        authService = new AuthServiceImpl(accountRepository, passwordEncoder, tokenIssuer,
                new AccountMapper(), eventPublisher, properties);
    }

    // --- Registration -------------------------------------------------------

    @Test
    @DisplayName("register: a passenger is stored with role PASSENGER and no event is published")
    void register_passenger_storesAccountAndPublishesNothing() {
        RegisterRequest request = request("nimal@ridelink.test");
        when(accountRepository.existsByEmail("nimal@ridelink.test")).thenReturn(false);
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(HASH);
        when(accountRepository.saveAndFlush(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountResponse response = authService.register(request, Role.PASSENGER);

        assertThat(response.role()).isEqualTo(Role.PASSENGER);
        assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);
        // Passenger registration has no downstream consumer, so it must stay silent.
        verify(eventPublisher, never()).publish(anyString(), any());
    }

    @Test
    @DisplayName("register: a driver publishes account.driver.registered with the new account id")
    void register_driver_publishesDriverRegisteredEvent() {
        RegisterRequest request = request("kamal@ridelink.test");
        when(accountRepository.existsByEmail("kamal@ridelink.test")).thenReturn(false);
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(HASH);
        when(accountRepository.saveAndFlush(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountResponse response = authService.register(request, Role.DRIVER);

        ArgumentCaptor<DriverRegisteredEvent> event = ArgumentCaptor.forClass(DriverRegisteredEvent.class);
        verify(eventPublisher).publish(eq(EventTypes.DRIVER_REGISTERED), event.capture());

        // The Driver service keys its profile shell off accountId, so this must match.
        assertThat(event.getValue().accountId()).isEqualTo(response.id());
        assertThat(event.getValue().email()).isEqualTo("kamal@ridelink.test");
        assertThat(event.getValue().phone()).isEqualTo("+94771234567");
    }

    @Test
    @DisplayName("register: a duplicate e-mail is rejected with EMAIL_ALREADY_REGISTERED")
    void register_duplicateEmail_throwsConflict() {
        when(accountRepository.existsByEmail("nimal@ridelink.test")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request("nimal@ridelink.test"), Role.PASSENGER))
                .isInstanceOf(EmailAlreadyRegisteredException.class)
                .hasMessageContaining("nimal@ridelink.test");

        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("register: e-mail is lower-cased, so casing cannot create a second account")
    void register_mixedCaseEmail_isNormalisedToLowerCase() {
        when(accountRepository.existsByEmail("nimal@ridelink.test")).thenReturn(false);
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(HASH);
        when(accountRepository.saveAndFlush(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountResponse response = authService.register(request("Nimal@RideLink.TEST"), Role.PASSENGER);

        assertThat(response.email()).isEqualTo("nimal@ridelink.test");
    }

    @Test
    @DisplayName("register: losing the race against the unique index still yields a clean 409")
    void register_uniqueIndexViolation_throwsConflict() {
        // Simulates two simultaneous registrations: existsByEmail says "free", but the
        // database rejects the insert. The unique index is the real guard.
        when(accountRepository.existsByEmail("nimal@ridelink.test")).thenReturn(false);
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(HASH);
        when(accountRepository.saveAndFlush(any(Account.class)))
                .thenThrow(new DataIntegrityViolationException("uk_account_email"));

        assertThatThrownBy(() -> authService.register(request("nimal@ridelink.test"), Role.PASSENGER))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    // --- Login --------------------------------------------------------------

    @Test
    @DisplayName("login: correct credentials return a token carrying the account id and role")
    void login_validCredentials_returnsToken() {
        Account account = activeAccount(Role.DRIVER);
        when(accountRepository.findByEmail("kamal@ridelink.test")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches(RAW_PASSWORD, account.getPasswordHash())).thenReturn(true);
        when(tokenIssuer.issueForAccount(account))
                .thenReturn(new TokenIssuer.IssuedToken("jwt-value", 3600, Instant.now().plusSeconds(3600)));

        LoginResponse response = authService.login(new LoginRequest("kamal@ridelink.test", RAW_PASSWORD));

        assertThat(response.accessToken()).isEqualTo("jwt-value");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600);
        assertThat(response.role()).isEqualTo(Role.DRIVER);
        assertThat(response.accountId()).isEqualTo(account.getId());
    }

    @Test
    @DisplayName("login: a wrong password is rejected and no token is issued")
    void login_wrongPassword_throwsInvalidCredentials() {
        Account account = activeAccount(Role.PASSENGER);
        when(accountRepository.findByEmail("kamal@ridelink.test")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("wrong", account.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("kamal@ridelink.test", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(tokenIssuer, never()).issueForAccount(any());
    }

    @Test
    @DisplayName("login: an unknown e-mail gives the same error as a wrong password")
    void login_unknownEmail_throwsInvalidCredentials() {
        when(accountRepository.findByEmail("ghost@ridelink.test")).thenReturn(Optional.empty());

        // Identical to the wrong-password case on purpose: differing responses would let
        // an attacker enumerate which e-mail addresses are registered.
        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@ridelink.test", RAW_PASSWORD)))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("login: a suspended account with correct credentials is refused with ACCOUNT_SUSPENDED")
    void login_suspendedAccount_throwsAccountNotActive() {
        Account account = activeAccount(Role.DRIVER);
        account.changeStatus(AccountStatus.SUSPENDED);
        when(accountRepository.findByEmail("kamal@ridelink.test")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches(RAW_PASSWORD, account.getPasswordHash())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("kamal@ridelink.test", RAW_PASSWORD)))
                .isInstanceOf(AccountNotActiveException.class)
                .extracting(ex -> ((AccountNotActiveException) ex).getCode())
                .isEqualTo(ErrorCodes.ACCOUNT_SUSPENDED);

        verify(tokenIssuer, never()).issueForAccount(any());
    }

    @Test
    @DisplayName("login: a deactivated account is refused with ACCOUNT_DEACTIVATED, not ACCOUNT_SUSPENDED")
    void login_deactivatedAccount_usesDeactivatedCode() {
        Account account = activeAccount(Role.PASSENGER);
        account.changeStatus(AccountStatus.DEACTIVATED);
        when(accountRepository.findByEmail("kamal@ridelink.test")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches(RAW_PASSWORD, account.getPasswordHash())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("kamal@ridelink.test", RAW_PASSWORD)))
                .extracting(ex -> ((AccountNotActiveException) ex).getCode())
                .isEqualTo(ErrorCodes.ACCOUNT_DEACTIVATED);
    }

    // --- Service tokens -----------------------------------------------------

    @Test
    @DisplayName("serviceToken: a known client with the right secret gets a SERVICE token")
    void issueServiceToken_validCredentials_returnsToken() {
        when(tokenIssuer.issueForServiceClient("ride-service"))
                .thenReturn(new TokenIssuer.IssuedToken("svc-jwt", 600, Instant.now().plusSeconds(600)));

        var response = authService.issueServiceToken(new ServiceTokenRequest("ride-service", "correct-secret"));

        assertThat(response.accessToken()).isEqualTo("svc-jwt");
        assertThat(response.expiresIn()).isEqualTo(600);
    }

    @Test
    @DisplayName("serviceToken: a wrong secret is rejected")
    void issueServiceToken_wrongSecret_throwsInvalidCredentials() {
        assertThatThrownBy(() ->
                authService.issueServiceToken(new ServiceTokenRequest("ride-service", "guessed")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(tokenIssuer, never()).issueForServiceClient(anyString());
    }

    @Test
    @DisplayName("serviceToken: an unknown client id is rejected")
    void issueServiceToken_unknownClient_throwsInvalidCredentials() {
        assertThatThrownBy(() ->
                authService.issueServiceToken(new ServiceTokenRequest("attacker-service", "anything")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    // --- Helpers ------------------------------------------------------------

    private static RegisterRequest request(String email) {
        return new RegisterRequest("Nimal Perera", email, "+94771234567", RAW_PASSWORD);
    }

    private static Account activeAccount(Role role) {
        return Account.create("Kamal Silva", "kamal@ridelink.test", "+94771234567", HASH, role);
    }

    /** Present so an unused-import warning does not hide a genuinely unused mock. */
    @SuppressWarnings("unused")
    private static UUID anyId() {
        return UUID.randomUUID();
    }
}
