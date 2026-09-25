package lk.ridelink.driver.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lk.ridelink.driver.config.RabbitConfig;
import lk.ridelink.driver.domain.Availability;
import lk.ridelink.driver.domain.DriverProfile;
import lk.ridelink.driver.domain.ServiceArea;
import lk.ridelink.driver.domain.VerificationStatus;
import lk.ridelink.driver.repository.DriverProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Profile creation and suspension propagation, both of which must tolerate redelivery. */
@ExtendWith(MockitoExtension.class)
class AccountEventConsumerTest {

    @Mock
    private DriverProfileRepository driverRepository;
    @Mock
    private ProcessedEventGuard processedEventGuard;

    private AccountEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new AccountEventConsumer(driverRepository, processedEventGuard, new ObjectMapper());
    }

    @Test
    @DisplayName("driver registration creates a PENDING, OFFLINE profile keyed by the account id")
    void onAccountEvent_driverRegistered_createsProfileShell() {
        UUID accountId = UUID.randomUUID();
        when(processedEventGuard.alreadyProcessed(any())).thenReturn(false);
        when(driverRepository.existsById(accountId)).thenReturn(false);

        consumer.onAccountEvent(envelope(RabbitConfig.ROUTING_DRIVER_REGISTERED, Map.of(
                "accountId", accountId.toString(),
                "fullName", "Kamal Silva",
                "email", "kamal@ridelink.test",
                "phone", "+94771234567")));

        ArgumentCaptor<DriverProfile> saved = ArgumentCaptor.forClass(DriverProfile.class);
        verify(driverRepository).save(saved.capture());

        // The account id becomes the driver id, which is what lets the Ride service refer
        // to a driver without any cross-service lookup.
        assertThat(saved.getValue().getDriverId()).isEqualTo(accountId);
        assertThat(saved.getValue().getVerificationStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(saved.getValue().getAvailability()).isEqualTo(Availability.OFFLINE);
        assertThat(saved.getValue().isAccountActive()).isTrue();
    }

    @Test
    @DisplayName("a redelivered registration does not overwrite an established profile")
    void onAccountEvent_driverAlreadyExists_doesNotOverwrite() {
        UUID accountId = UUID.randomUUID();
        when(processedEventGuard.alreadyProcessed(any())).thenReturn(false);
        when(driverRepository.existsById(accountId)).thenReturn(true);

        consumer.onAccountEvent(envelope(RabbitConfig.ROUTING_DRIVER_REGISTERED, Map.of(
                "accountId", accountId.toString(),
                "fullName", "Kamal Silva",
                "email", "kamal@ridelink.test",
                "phone", "+94771234567")));

        // Overwriting would wipe a verified driver's licence and vehicle back to a blank
        // shell - the worst possible outcome of an ordinary redelivery.
        verify(driverRepository, never()).save(any());
    }

    @Test
    @DisplayName("suspension forces an online driver offline so they stop being matched")
    void onAccountEvent_suspension_forcesAvailableDriverOffline() {
        DriverProfile driver = availableDriver();
        when(processedEventGuard.alreadyProcessed(any())).thenReturn(false);
        when(driverRepository.findById(driver.getDriverId())).thenReturn(Optional.of(driver));

        consumer.onAccountEvent(envelope(RabbitConfig.ROUTING_ACCOUNT_STATUS_CHANGED, Map.of(
                "accountId", driver.getDriverId().toString(),
                "role", "DRIVER",
                "oldStatus", "ACTIVE",
                "newStatus", "SUSPENDED")));

        assertThat(driver.isAccountActive()).isFalse();
        // Matching never calls Account, so without this the driver would keep taking rides.
        assertThat(driver.getAvailability()).isEqualTo(Availability.OFFLINE);
        verify(driverRepository).save(driver);
    }

    @Test
    @DisplayName("suspension leaves a driver mid-ride BUSY rather than disrupting the trip")
    void onAccountEvent_suspensionWhileBusy_doesNotInterruptRide() {
        DriverProfile driver = availableDriver();
        driver.reserveFor(UUID.randomUUID());
        when(processedEventGuard.alreadyProcessed(any())).thenReturn(false);
        when(driverRepository.findById(driver.getDriverId())).thenReturn(Optional.of(driver));

        consumer.onAccountEvent(envelope(RabbitConfig.ROUTING_ACCOUNT_STATUS_CHANGED, Map.of(
                "accountId", driver.getDriverId().toString(),
                "role", "DRIVER",
                "oldStatus", "ACTIVE",
                "newStatus", "SUSPENDED")));

        // The passenger is in the vehicle; yanking the driver offline would strand them.
        // The suspension takes effect when the ride ends.
        assertThat(driver.isAccountActive()).isFalse();
        assertThat(driver.getAvailability()).isEqualTo(Availability.BUSY);
    }

    @Test
    @DisplayName("reactivation restores accountActive without putting the driver online")
    void onAccountEvent_reactivation_setsAccountActiveButLeavesOffline() {
        DriverProfile driver = availableDriver();
        driver.setAccountActive(false);
        when(processedEventGuard.alreadyProcessed(any())).thenReturn(false);
        when(driverRepository.findById(driver.getDriverId())).thenReturn(Optional.of(driver));

        consumer.onAccountEvent(envelope(RabbitConfig.ROUTING_ACCOUNT_STATUS_CHANGED, Map.of(
                "accountId", driver.getDriverId().toString(),
                "role", "DRIVER",
                "oldStatus", "SUSPENDED",
                "newStatus", "ACTIVE")));

        assertThat(driver.isAccountActive()).isTrue();
        // Going back online is the driver's decision, not an automatic consequence.
        assertThat(driver.getAvailability()).isEqualTo(Availability.OFFLINE);
    }

    @Test
    @DisplayName("a passenger status change is ignored: it has no driver profile")
    void onAccountEvent_passengerStatusChange_isIgnored() {
        when(processedEventGuard.alreadyProcessed(any())).thenReturn(false);

        consumer.onAccountEvent(envelope(RabbitConfig.ROUTING_ACCOUNT_STATUS_CHANGED, Map.of(
                "accountId", UUID.randomUUID().toString(),
                "role", "PASSENGER",
                "oldStatus", "ACTIVE",
                "newStatus", "SUSPENDED")));

        verify(driverRepository, never()).findById(any());
    }

    @Test
    @DisplayName("a duplicate account event is skipped entirely")
    void onAccountEvent_duplicateEvent_isSkipped() {
        when(processedEventGuard.alreadyProcessed(any())).thenReturn(true);

        consumer.onAccountEvent(envelope(RabbitConfig.ROUTING_DRIVER_REGISTERED, Map.of(
                "accountId", UUID.randomUUID().toString(),
                "fullName", "Kamal Silva",
                "email", "kamal@ridelink.test",
                "phone", "+94771234567")));

        verify(driverRepository, never()).existsById(any());
        verify(driverRepository, never()).save(any());
    }

    private static EventEnvelope<Object> envelope(String eventType, Object payload) {
        return new EventEnvelope<>(UUID.randomUUID(), eventType, 1, Instant.now(),
                UUID.randomUUID().toString(), payload);
    }

    private static DriverProfile availableDriver() {
        DriverProfile driver = DriverProfile.shellFor(UUID.randomUUID(), "Kamal Silva", "+94771234567");
        driver.setVerificationStatus(VerificationStatus.VERIFIED);
        driver.updateDetails("B1234567", LocalDate.now().plusYears(2), ServiceArea.NEGOMBO);
        driver.updateLocation(7.2083, 79.8358);
        driver.goAvailable();
        return driver;
    }
}
