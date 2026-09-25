package lk.ridelink.ride.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lk.ridelink.ride.domain.Location;
import lk.ridelink.ride.domain.PaymentMethod;
import lk.ridelink.ride.domain.PaymentStatus;
import lk.ridelink.ride.domain.Ride;
import lk.ridelink.ride.domain.RideStatus;
import lk.ridelink.ride.domain.ServiceArea;
import lk.ridelink.ride.domain.VehicleType;
import lk.ridelink.ride.repository.RideRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Keeping the ride's payment read model correct, including out-of-order delivery. */
@ExtendWith(MockitoExtension.class)
class PaymentEventConsumerTest {

    @Mock
    private RideRepository rideRepository;
    @Mock
    private ProcessedEventGuard processedEventGuard;

    private PaymentEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new PaymentEventConsumer(rideRepository, processedEventGuard, new ObjectMapper());
    }

    @Test
    @DisplayName("payment.succeeded marks the ride PAID")
    void onPaymentEvent_succeeded_marksRidePaid() {
        Ride ride = completedRide();
        when(processedEventGuard.alreadyProcessed(any())).thenReturn(false);
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));

        consumer.onPaymentEvent(envelope(EventTypes.PAYMENT_SUCCEEDED, Map.of(
                "paymentId", UUID.randomUUID().toString(),
                "rideId", ride.getId().toString(),
                "receiptNumber", "RL-2026-000123")));

        assertThat(ride.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        verify(rideRepository).save(ride);
    }

    @Test
    @DisplayName("payment.failed marks the ride FAILED")
    void onPaymentEvent_failed_marksRideFailed() {
        Ride ride = completedRide();
        when(processedEventGuard.alreadyProcessed(any())).thenReturn(false);
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));

        consumer.onPaymentEvent(envelope(EventTypes.PAYMENT_FAILED, Map.of(
                "paymentId", UUID.randomUUID().toString(),
                "rideId", ride.getId().toString(),
                "failureReason", "CARD_DECLINED")));

        assertThat(ride.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("a late payment.failed cannot undo an already PAID ride")
    void onPaymentEvent_failedAfterPaid_doesNotOverwrite() {
        Ride ride = completedRide();
        ride.updatePaymentStatus(PaymentStatus.PAID);
        when(processedEventGuard.alreadyProcessed(any())).thenReturn(false);
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));

        consumer.onPaymentEvent(envelope(EventTypes.PAYMENT_FAILED, Map.of(
                "paymentId", UUID.randomUUID().toString(),
                "rideId", ride.getId().toString(),
                "failureReason", "CARD_DECLINED")));

        // A decline followed by a successful retry produces two events whose arrival order
        // is not guaranteed. Without this guard, a paid ride could end up showing FAILED.
        assertThat(ride.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        verify(rideRepository, never()).save(any());
    }

    @Test
    @DisplayName("a retry after a decline does move FAILED to PAID")
    void onPaymentEvent_succeededAfterFailed_updatesToPaid() {
        Ride ride = completedRide();
        ride.updatePaymentStatus(PaymentStatus.FAILED);
        when(processedEventGuard.alreadyProcessed(any())).thenReturn(false);
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));

        consumer.onPaymentEvent(envelope(EventTypes.PAYMENT_SUCCEEDED, Map.of(
                "paymentId", UUID.randomUUID().toString(),
                "rideId", ride.getId().toString(),
                "receiptNumber", "RL-2026-000124")));

        // FAILED is not terminal, so this direction must be allowed.
        assertThat(ride.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    @DisplayName("a duplicate event is skipped entirely")
    void onPaymentEvent_duplicate_isSkipped() {
        when(processedEventGuard.alreadyProcessed(any())).thenReturn(true);

        consumer.onPaymentEvent(envelope(EventTypes.PAYMENT_SUCCEEDED, Map.of(
                "paymentId", UUID.randomUUID().toString(),
                "rideId", UUID.randomUUID().toString(),
                "receiptNumber", "RL-2026-000123")));

        verify(rideRepository, never()).findById(any());
        verify(processedEventGuard, never()).record(any(), anyString());
    }

    @Test
    @DisplayName("an event for an unknown ride is acknowledged rather than dead-lettered")
    void onPaymentEvent_unknownRide_isIgnored() {
        when(processedEventGuard.alreadyProcessed(any())).thenReturn(false);
        when(rideRepository.findById(any())).thenReturn(Optional.empty());

        consumer.onPaymentEvent(envelope(EventTypes.PAYMENT_SUCCEEDED, Map.of(
                "paymentId", UUID.randomUUID().toString(),
                "rideId", UUID.randomUUID().toString(),
                "receiptNumber", "RL-2026-000123")));

        // Retrying a message that can never succeed would only fill the DLQ.
        verify(rideRepository, never()).save(any());
    }

    private static EventEnvelope<Object> envelope(String eventType, Object payload) {
        return new EventEnvelope<>(UUID.randomUUID(), eventType, 1, Instant.now(),
                UUID.randomUUID().toString(), payload);
    }

    private static Ride completedRide() {
        Ride ride = Ride.request(UUID.randomUUID(),
                new Location("Colombo Fort", 6.9344, 79.8428),
                new Location("Negombo", 7.2083, 79.8358),
                VehicleType.CAR, ServiceArea.NEGOMBO, PaymentMethod.CARD);
        ride.applyFareEstimate(UUID.randomUUID(), new BigDecimal("4595.00"),
                new BigDecimal("39.65"), 96, "LKR");
        ride.assignDriver(UUID.randomUUID());
        ride.transitionTo(RideStatus.ASSIGNED);
        ride.transitionTo(RideStatus.ACCEPTED);
        ride.transitionTo(RideStatus.IN_PROGRESS);
        ride.transitionTo(RideStatus.COMPLETED);
        return ride;
    }
}
