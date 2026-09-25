package lk.ridelink.driver.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Consumer behaviour for ride events, with idempotency as the focus.
 *
 * <p>RabbitMQ delivers at least once, so these handlers <em>will</em> see duplicates in
 * production. A double-applied release or a double-counted trip would be a silent data
 * error, which is exactly the kind of bug an integration demo never surfaces.</p>
 */
@ExtendWith(MockitoExtension.class)
class RideEventConsumerTest {

    @Mock
    private DriverProfileRepository driverRepository;
    @Mock
    private ProcessedEventGuard processedEventGuard;

    private RideEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new RideEventConsumer(driverRepository, processedEventGuard, new ObjectMapper());
    }

    @Test
    @DisplayName("ride.completed releases the driver and credits the trip")
    void onRideEvent_rideCompleted_releasesDriverAndIncrementsTrips() {
        DriverProfile driver = busyDriver();
        UUID rideId = driver.getCurrentRideId();
        when(processedEventGuard.alreadyProcessed(any())).thenReturn(false);
        when(driverRepository.findById(driver.getDriverId())).thenReturn(Optional.of(driver));

        consumer.onRideEvent(envelope(RabbitConfig.ROUTING_RIDE_COMPLETED,
                Map.of("rideId", rideId.toString(), "driverId", driver.getDriverId().toString())));

        assertThat(driver.getAvailability()).isEqualTo(Availability.AVAILABLE);
        assertThat(driver.getCurrentRideId()).isNull();
        assertThat(driver.getCompletedTrips()).isEqualTo(1);
        verify(driverRepository).save(driver);
    }

    @Test
    @DisplayName("a redelivered ride.completed is skipped, so the trip is not counted twice")
    void onRideEvent_duplicateEvent_isSkipped() {
        when(processedEventGuard.alreadyProcessed(any())).thenReturn(true);

        consumer.onRideEvent(envelope(RabbitConfig.ROUTING_RIDE_COMPLETED,
                Map.of("rideId", UUID.randomUUID().toString(),
                        "driverId", UUID.randomUUID().toString())));

        // Nothing is read and nothing is written: the guard short-circuits the handler.
        verify(driverRepository, never()).findById(any());
        verify(driverRepository, never()).save(any());
        verify(processedEventGuard, never()).record(any(), anyString());
    }

    @Test
    @DisplayName("a handled event is recorded, so the next delivery is recognised as a duplicate")
    void onRideEvent_newEvent_isRecordedAfterHandling() {
        DriverProfile driver = busyDriver();
        when(processedEventGuard.alreadyProcessed(any())).thenReturn(false);
        when(driverRepository.findById(driver.getDriverId())).thenReturn(Optional.of(driver));

        consumer.onRideEvent(envelope(RabbitConfig.ROUTING_RIDE_COMPLETED,
                Map.of("rideId", driver.getCurrentRideId().toString(),
                        "driverId", driver.getDriverId().toString())));

        verify(processedEventGuard).record(any(UUID.class), anyString());
    }

    @Test
    @DisplayName("ride.cancelled releases the driver without crediting a trip")
    void onRideEvent_rideCancelled_releasesWithoutCountingTrip() {
        DriverProfile driver = busyDriver();
        when(processedEventGuard.alreadyProcessed(any())).thenReturn(false);
        when(driverRepository.findById(driver.getDriverId())).thenReturn(Optional.of(driver));

        consumer.onRideEvent(envelope(RabbitConfig.ROUTING_RIDE_CANCELLED,
                Map.of("rideId", driver.getCurrentRideId().toString(),
                        "driverId", driver.getDriverId().toString())));

        assertThat(driver.getAvailability()).isEqualTo(Availability.AVAILABLE);
        // No trip was made, so the driver's record must not improve.
        assertThat(driver.getCompletedTrips()).isZero();
    }

    @Test
    @DisplayName("ride.cancelled with no driver is ignored: nobody had been reserved")
    void onRideEvent_cancelledBeforeAssignment_doesNothing() {
        when(processedEventGuard.alreadyProcessed(any())).thenReturn(false);

        consumer.onRideEvent(envelope(RabbitConfig.ROUTING_RIDE_CANCELLED,
                Map.of("rideId", UUID.randomUUID().toString())));

        verify(driverRepository, never()).findById(any());
        verify(driverRepository, never()).save(any());
    }

    @Test
    @DisplayName("a stale event for an old ride does not free a driver now on a new ride")
    void onRideEvent_eventForDifferentRide_leavesDriverBusy() {
        DriverProfile driver = busyDriver();
        UUID currentRide = driver.getCurrentRideId();
        when(processedEventGuard.alreadyProcessed(any())).thenReturn(false);
        when(driverRepository.findById(driver.getDriverId())).thenReturn(Optional.of(driver));

        consumer.onRideEvent(envelope(RabbitConfig.ROUTING_RIDE_COMPLETED,
                Map.of("rideId", UUID.randomUUID().toString(),
                        "driverId", driver.getDriverId().toString())));

        // Strands nobody: the driver stays on the ride they are actually doing.
        assertThat(driver.getAvailability()).isEqualTo(Availability.BUSY);
        assertThat(driver.getCurrentRideId()).isEqualTo(currentRide);
        verify(driverRepository, never()).save(any());
    }

    @Test
    @DisplayName("an event for an unknown driver is acknowledged rather than dead-lettered")
    void onRideEvent_unknownDriver_isIgnoredWithoutThrowing() {
        when(processedEventGuard.alreadyProcessed(any())).thenReturn(false);
        when(driverRepository.findById(any())).thenReturn(Optional.empty());

        consumer.onRideEvent(envelope(RabbitConfig.ROUTING_RIDE_COMPLETED,
                Map.of("rideId", UUID.randomUUID().toString(),
                        "driverId", UUID.randomUUID().toString())));

        // Throwing would retry three times and then dead-letter, for a message that can
        // never succeed. Acknowledging keeps the queue moving.
        verify(driverRepository, never()).save(any());
    }

    @Test
    @DisplayName("an unrecognised event type is ignored, not dead-lettered")
    void onRideEvent_unknownEventType_isIgnored() {
        when(processedEventGuard.alreadyProcessed(any())).thenReturn(false);

        consumer.onRideEvent(envelope("ride.something.new", Map.of()));

        verify(driverRepository, never()).save(any());
    }

    private static EventEnvelope<Object> envelope(String eventType, Object payload) {
        return new EventEnvelope<>(UUID.randomUUID(), eventType, 1, Instant.now(),
                UUID.randomUUID().toString(), payload);
    }

    private static DriverProfile busyDriver() {
        DriverProfile driver = DriverProfile.shellFor(UUID.randomUUID(), "Kamal Silva", "+94771234567");
        driver.setVerificationStatus(VerificationStatus.VERIFIED);
        driver.updateDetails("B1234567", LocalDate.now().plusYears(2), ServiceArea.NEGOMBO);
        driver.updateLocation(7.2083, 79.8358);
        driver.goAvailable();
        driver.reserveFor(UUID.randomUUID());
        return driver;
    }
}
