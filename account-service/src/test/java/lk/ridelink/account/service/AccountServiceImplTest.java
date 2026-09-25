package lk.ridelink.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import lk.ridelink.account.domain.Account;
import lk.ridelink.account.domain.AccountStatus;
import lk.ridelink.account.domain.Role;
import lk.ridelink.account.dto.AccountResponse;
import lk.ridelink.account.dto.ChangePasswordRequest;
import lk.ridelink.account.dto.ChangeStatusRequest;
import lk.ridelink.account.dto.UpdateProfileRequest;
import lk.ridelink.account.exception.BusinessRuleException;
import lk.ridelink.account.exception.InvalidCredentialsException;
import lk.ridelink.account.exception.NotFoundException;
import lk.ridelink.account.mapper.AccountMapper;
import lk.ridelink.account.messaging.AccountStatusChangedEvent;
import lk.ridelink.account.messaging.EventPublisher;
import lk.ridelink.account.messaging.EventTypes;
import lk.ridelink.account.repository.AccountRepository;
import lk.ridelink.account.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Unit tests for profile and admin operations, focused on the authorisation rules that
 * cannot be expressed with an annotation and therefore live in the service layer.
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EventPublisher eventPublisher;
    @Mock
    private CurrentUser currentUser;

    private AccountServiceImpl accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountServiceImpl(accountRepository, new AccountMapper(),
                passwordEncoder, eventPublisher, currentUser);
    }

    // --- Own profile --------------------------------------------------------

    @Test
    @DisplayName("getOwnProfile: returns the caller's account and never the password hash")
    void getOwnProfile_authenticatedUser_returnsOwnAccount() {
        Account account = passenger();
        when(currentUser.id()).thenReturn(account.getId());
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        AccountResponse response = accountService.getOwnProfile();

        assertThat(response.id()).isEqualTo(account.getId());
        assertThat(response.email()).isEqualTo("nimal@ridelink.test");
        // AccountResponse has no hash field at all; asserted here so the guarantee is
        // visible as a test, not just as a code-review habit.
        assertThat(AccountResponse.class.getRecordComponents())
                .noneMatch(component -> component.getName().toLowerCase().contains("password"));
    }

    @Test
    @DisplayName("updateOwnProfile: changes name and phone but leaves e-mail and role alone")
    void updateOwnProfile_validRequest_updatesOnlyNameAndPhone() {
        Account account = passenger();
        when(currentUser.id()).thenReturn(account.getId());
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(accountRepository.save(account)).thenReturn(account);

        AccountResponse response = accountService.updateOwnProfile(
                new UpdateProfileRequest("Nimal J Perera", "+94719999999"));

        assertThat(response.fullName()).isEqualTo("Nimal J Perera");
        assertThat(response.phone()).isEqualTo("+94719999999");
        assertThat(response.email()).isEqualTo("nimal@ridelink.test");
        assertThat(response.role()).isEqualTo(Role.PASSENGER);
    }

    @Test
    @DisplayName("changeOwnPassword: the correct current password re-hashes and saves")
    void changeOwnPassword_correctCurrentPassword_updatesHash() {
        Account account = passenger();
        when(currentUser.id()).thenReturn(account.getId());
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("OldPass123", account.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.encode("NewPass456")).thenReturn("$2a$10$newhash");

        accountService.changeOwnPassword(new ChangePasswordRequest("OldPass123", "NewPass456"));

        assertThat(account.getPasswordHash()).isEqualTo("$2a$10$newhash");
        verify(accountRepository).save(account);
    }

    @Test
    @DisplayName("changeOwnPassword: a wrong current password is refused and nothing is saved")
    void changeOwnPassword_wrongCurrentPassword_throwsAndDoesNotSave() {
        Account account = passenger();
        when(currentUser.id()).thenReturn(account.getId());
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("guess", account.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() ->
                accountService.changeOwnPassword(new ChangePasswordRequest("guess", "NewPass456")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(accountRepository, never()).save(any());
    }

    // --- Reading another account -------------------------------------------

    @Test
    @DisplayName("getById: an admin may read any account")
    void getById_callerIsAdmin_returnsAccount() {
        Account account = passenger();
        when(currentUser.isAdmin()).thenReturn(true);
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        assertThat(accountService.getById(account.getId()).id()).isEqualTo(account.getId());
    }

    @Test
    @DisplayName("getById: a user may read their own account")
    void getById_callerIsOwner_returnsAccount() {
        Account account = passenger();
        when(currentUser.isAdmin()).thenReturn(false);
        when(currentUser.is(account.getId())).thenReturn(true);
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        assertThat(accountService.getById(account.getId()).id()).isEqualTo(account.getId());
    }

    @Test
    @DisplayName("getById: a non-admin reading someone else's account is denied")
    void getById_callerIsNeitherAdminNorOwner_throwsAccessDenied() {
        UUID someoneElse = UUID.randomUUID();
        when(currentUser.isAdmin()).thenReturn(false);
        when(currentUser.is(someoneElse)).thenReturn(false);

        assertThatThrownBy(() -> accountService.getById(someoneElse))
                .isInstanceOf(AccessDeniedException.class);

        // The account is never even loaded, so a 403 cannot be told apart from a 404 by
        // timing - it does not reveal whether that id exists.
        verify(accountRepository, never()).findById(any());
    }

    @Test
    @DisplayName("getById: an unknown id gives 404")
    void getById_unknownId_throwsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(currentUser.isAdmin()).thenReturn(true);
        when(accountRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getById(unknown))
                .isInstanceOf(NotFoundException.class);
    }

    // --- Admin status changes ----------------------------------------------

    @Test
    @DisplayName("changeStatus: suspending an account publishes account.status.changed")
    void changeStatus_statusMoves_publishesEvent() {
        Account driver = driver();
        when(currentUser.is(driver.getId())).thenReturn(false);
        when(accountRepository.findById(driver.getId())).thenReturn(Optional.of(driver));
        when(accountRepository.save(driver)).thenReturn(driver);

        AccountResponse response = accountService.changeStatus(driver.getId(),
                new ChangeStatusRequest(AccountStatus.SUSPENDED, "Repeated cancellations"));

        assertThat(response.status()).isEqualTo(AccountStatus.SUSPENDED);

        ArgumentCaptor<AccountStatusChangedEvent> event =
                ArgumentCaptor.forClass(AccountStatusChangedEvent.class);
        verify(eventPublisher).publish(eq(EventTypes.ACCOUNT_STATUS_CHANGED), event.capture());

        assertThat(event.getValue().accountId()).isEqualTo(driver.getId());
        assertThat(event.getValue().role()).isEqualTo("DRIVER");
        assertThat(event.getValue().oldStatus()).isEqualTo("ACTIVE");
        assertThat(event.getValue().newStatus()).isEqualTo("SUSPENDED");
    }

    @Test
    @DisplayName("changeStatus: an admin cannot change their own status")
    void changeStatus_adminTargetsSelf_throwsBusinessRule() {
        UUID adminId = UUID.randomUUID();
        when(currentUser.is(adminId)).thenReturn(true);

        // Without this rule an admin could suspend themselves and, if they were the only
        // admin, lock the whole system out of any further administration.
        assertThatThrownBy(() -> accountService.changeStatus(adminId,
                new ChangeStatusRequest(AccountStatus.SUSPENDED, "oops")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("own account status");

        verify(accountRepository, never()).save(any());
        verify(eventPublisher, never()).publish(anyString(), any());
    }

    @Test
    @DisplayName("changeStatus: setting the status it already has publishes nothing")
    void changeStatus_sameStatus_isNoOpAndPublishesNothing() {
        Account driver = driver();
        when(currentUser.is(driver.getId())).thenReturn(false);
        when(accountRepository.findById(driver.getId())).thenReturn(Optional.of(driver));

        AccountResponse response = accountService.changeStatus(driver.getId(),
                new ChangeStatusRequest(AccountStatus.ACTIVE, "no change"));

        assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);
        // Consumers are idempotent, but a no-op event would still make them do work and
        // would muddy the event log during the demo.
        verify(eventPublisher, never()).publish(anyString(), any());
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("changeStatus: reactivating a suspended account publishes the reverse transition")
    void changeStatus_reactivation_publishesSuspendedToActive() {
        Account driver = driver();
        driver.changeStatus(AccountStatus.SUSPENDED);
        when(currentUser.is(driver.getId())).thenReturn(false);
        when(accountRepository.findById(driver.getId())).thenReturn(Optional.of(driver));
        when(accountRepository.save(driver)).thenReturn(driver);

        accountService.changeStatus(driver.getId(),
                new ChangeStatusRequest(AccountStatus.ACTIVE, "Appeal upheld"));

        ArgumentCaptor<AccountStatusChangedEvent> event =
                ArgumentCaptor.forClass(AccountStatusChangedEvent.class);
        verify(eventPublisher).publish(eq(EventTypes.ACCOUNT_STATUS_CHANGED), event.capture());

        assertThat(event.getValue().oldStatus()).isEqualTo("SUSPENDED");
        assertThat(event.getValue().newStatus()).isEqualTo("ACTIVE");
    }

    // --- Helpers ------------------------------------------------------------

    private static Account passenger() {
        return Account.create("Nimal Perera", "nimal@ridelink.test", "+94771234567",
                "$2a$10$hashed", Role.PASSENGER);
    }

    private static Account driver() {
        return Account.create("Kamal Silva", "kamal@ridelink.test", "+94771234567",
                "$2a$10$hashed", Role.DRIVER);
    }
}
