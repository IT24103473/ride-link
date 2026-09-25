package lk.ridelink.payment.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lk.ridelink.payment.config.RideLinkProperties;
import lk.ridelink.payment.domain.DistanceTimeFareCalculator;
import lk.ridelink.payment.domain.FareType;
import lk.ridelink.payment.domain.FinalFare;
import lk.ridelink.payment.domain.Payment;
import lk.ridelink.payment.domain.PaymentStatus;
import lk.ridelink.payment.domain.VehicleType;
import lk.ridelink.payment.repository.FinalFareRepository;
import lk.ridelink.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Final fares and cancellation fees, as driven by ride events.
 *
 * <p>The cancellation-fee rule gets every combination of who cancelled and from which
 * status, because charging a fee that is not owed - or failing to charge one that is - is
 * a money error that no other test would catch.</p>
 */
@ExtendWith(MockitoExtension.class)
class RideEventConsumerTest {

    @Mock
    private FinalFareRepository finalFareRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private ProcessedEventGuard processedEventGuard;

    private RideEventConsumer consumer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        RideLinkProperties properties = new RideLinkProperties(
                new RideLinkProperties.Security("secret"),
                new RideLinkProperties.Fare("LKR", new BigDecimal("1.3"), new BigDecimal("25"),
                        15, new BigDecimal("100.00")),
                Map.of(
                        VehicleType.TUK, tariff("100.00", "80.00", "3.00", "200.00"),
                        VehicleType.CAR, tariff("150.00", "100.00", "5.00", "300.00"),
                        VehicleType.VAN, tariff("250.00", "140.00", "7.00", "500.00")));

        // JavaTimeModule so Instant fields in the event payload deserialise.
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        consumer = new RideEventConsumer(finalFareRepository, paymentRepository,
                new DistanceTimeFareCalculator(properties), processedEventGuard,
                properties, objectMapper);
    }

    // --- Completed rides ----------------------------------------------------

    @Test
    @DisplayName("ride.completed creates a final fare and a PENDING payment")
    void onRideEvent_rideCompleted_createsFareAndPendingPayment() {
        UUID rideId = UUID.randomUUID();
        when(processedEventGuard.alreadyProcessed(any())).thenReturn(false);
        when(paymentRepository.existsByRideIdAndType(rideId, FareType.TRIP)).thenReturn(false);

        consumer.onRideEvent(completedEvent(rideId, 10.0, null,
                Instant.parse("2026-09-28T09:30:00Z"), Instant.parse("2026-09-28T10:00:00Z")));

        ArgumentCaptor<Payment> payment = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(payment.capture());

        assertThat(payment.getValue().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getValue().getType()).isEqualTo(FareType.TRIP);
        assertThat(payment.getValue().getRideId()).isEqualTo(rideId);
    }

    @Test
    @DisplayName("the final fare uses the driver's reported distance when there is one")
    void onRideEvent_actualDistanceReported_isPreferredOverTheEstimate() {
        UUID rideId = UUID.randomUUID();
        when(processedEventGuard.alreadyProcessed(any())).thenReturn(false);
        when(paymentRepository.existsByRideIdAndType(rideId, FareType.TRIP)).thenReturn(false);

        // Estimated 10 km, actually drove 12.5 km.
        consumer.onRideEvent(completedEvent(rideId, 10.0, 12.5,
                Instant.parse("2026-09-28T09:30:00Z"), Instant.parse("2026-09-28T10:00:00Z")));

        ArgumentCaptor<FinalFare> fare = ArgumentCaptor.forClass(FinalFare.class);
        verify(finalFareRepository).save(fare.capture());

        assertThat(fare.getValue().getBreakdown().distanceKm()).isEqualByComparingTo("12.50");
    }

    @Test
    @DisplayName("the estimate is used when the driver reported no distance")
    void onRideEvent_noActualDistance_fallsBackToEstimate() {
        UUID rideId = UUID.randomUUID();
        when(processedEventGuard.alreadyProcessed(any())).thenReturn(false);
        when(paymentRepository.existsByRideIdAndType(rideId, FareType.TRIP)).thenReturn(false);

        consumer.onRideEvent(completedEvent(rideId, 10.0, null,
                Instant.parse("2026-09-28T09:30:00Z"), Instant.parse("2026-09-28T10:00:00Z")));

        ArgumentCaptor<FinalFare> fare = ArgumentCaptor.forClass(FinalFare.class);
        verify(finalFareRepository).save(fare.capture());

        assertThat(fare.getValue().getBreakdown().distanceKm()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("duration comes from the real elapsed time, not the estimate")
    void onRideEvent_usesActualElapsedTime() {
        UUID rideId = UUID.randomUUID();
        when(processedEventGuard.alreadyProcessed(any())).thenReturn(false);
        when(paymentRepository.existsByRideIdAndType(rideId, FareType.TRIP)).thenReturn(false);

        // 09:30 to 10:00 is exactly 30 minutes.
        consumer.onRideEvent(completedEvent(rideId, 10.0, null,
                Instant.parse("2026-09-28T09:30:00Z"), Instant.parse("2026-09-28T10:00:00Z")));

        ArgumentCaptor<FinalFare> fare = ArgumentCaptor.forClass(FinalFare.class);
        verify(finalFareRepository).save(fare.capture());

        // This is why the final fare usually differs from the estimate, which assumed
        // a fixed 25 km/h.
        assertThat(fare.getValue().getBreakdown().durationMin()).isEqualTo(30);
    }

    @Test
    @DisplayName("a sub-minute trip is billed one minute, never zero (boundary)")
    void onRideEvent_veryShortTrip_billsMinimumOneMinute() {
        UUID rideId = UUID.randomUUID();
        when(processedEventGuard.alreadyProcessed(any())).thenReturn(false);
        when(paymentRepository.existsByRideIdAndType(rideId, FareType.TRIP)).thenReturn(false);

        consumer.onRideEvent(completedEvent(rideId, 0.5, null,
                Instant.parse("2026-09-28T09:30:00Z"), Instant.parse("2026-09-28T09:30:40Z")));

        ArgumentCaptor<FinalFare> fare = ArgumentCaptor.forClass(FinalFare.class);
        verify(finalFareRepository).save(fare.capture());

        assertThat(fare.getValue().getBreakdown().durationMin()).isEqualTo(1);
    }

    @Test
    @DisplayName("elapsed time is rounded up, so a trip is never billed short")
    void onRideEvent_partialMinute_roundsUp() {
        UUID rideId = UUID.randomUUID();
        when(processedEventGuard.alreadyProcessed(any())).thenReturn(false);
        when(paymentRepository.existsByRideIdAndType(rideId, FareType.TRIP)).thenReturn(false);

        // 30 minutes and 1 second becomes 31 minutes.
        consumer.onRideEvent(completedEvent(rideId, 10.0, null,
                Instant.parse("2026-09-28T09:30:00Z"), Instant.parse("2026-09-28T10:00:01Z")));

        ArgumentCaptor<FinalFare> fare = ArgumentCaptor.forClass(FinalFare.class);
        verify(finalFareRepository).save(fare.capture());

        assertThat(fare.getValue().getBreakdown().durationMin()).isEqualTo(31);
    }

    // --- Idempotency --------------------------------------------------------

    @Test
    @DisplayName("a redelivered ride.completed does not create a second charge")
    void onRideEvent_tripFareAlreadyExists_createsNothing() {
        UUID rideId = UUID.randomUUID();
        when(processedEventGuard.alreadyProcessed(any())).thenReturn(false);
        when(paymentRepository.existsByRideIdAndType(rideId, FareType.TRIP)).thenReturn(true);

        consumer.onRideEvent(completedEvent(rideId, 10.0, null,
                Instant.parse("2026-09-28T09:30:00Z"), Instant.parse("2026-09-28T10:00:00Z")));

        // Backed by the (ride_id, type) unique index, so even this check failing could not
        // produce a double charge.
        verify(finalFareRepository, never()).save(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("an event already in processed_event is skipped entirely")
    void onRideEvent_duplicateEventId_isSkipped() {
        when(processedEventGuard.alreadyProcessed(any())).thenReturn(true);

        consumer.onRideEvent(completedEvent(UUID.randomUUID(), 10.0, null,
                Instant.parse("2026-09-28T09:30:00Z"), Instant.parse("2026-09-28T10:00:00Z")));

        verify(paymentRepository, never()).existsByRideIdAndType(any(), any());
        verify(finalFareRepository, never()).save(any());
    }

    // --- Cancellation fee: all four combinations ---------------------------

    @Test
    @DisplayName("PASSENGER cancelling from ACCEPTED is charged the fee")
    void onRideEvent_passengerCancelsFromAccepted_chargesFee() {
        UUID rideId = UUID.randomUUID();
        when(processedEventGuard.alreadyProcessed(any())).thenReturn(false);
        when(paymentRepository.existsByRideIdAndType(rideId, FareType.CANCELLATION_FEE))
                .thenReturn(false);

        consumer.onRideEvent(cancelledEvent(rideId, "PASSENGER", "ACCEPTED"));

        ArgumentCaptor<Payment> payment = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(payment.capture());

        // The fee is only fair once a driver has committed and begun travelling.
        assertThat(payment.getValue().getType()).isEqualTo(FareType.CANCELLATION_FEE);
        assertThat(payment.getValue().getAmount()).isEqualByComparingTo("100.00");
    }

    @ParameterizedTest(name = "{0} cancelling from {1} incurs no fee")
    @CsvSource({
            "PASSENGER,REQUESTED",
            "PASSENGER,ASSIGNED",
            "DRIVER,ACCEPTED",
            "ADMIN,ACCEPTED"
    })
    @DisplayName("every other combination of who and when incurs no fee at all")
    void onRideEvent_otherCancellations_chargeNothing(String cancelledBy, String previousStatus) {
        when(processedEventGuard.alreadyProcessed(any())).thenReturn(false);

        consumer.onRideEvent(cancelledEvent(UUID.randomUUID(), cancelledBy, previousStatus));

        // Nothing is created at all, so GET /payments/rides/{id} correctly stays 404:
        // there is genuinely nothing to pay.
        verify(finalFareRepository, never()).save(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("a redelivered cancellation does not charge the fee twice")
    void onRideEvent_cancellationFeeAlreadyExists_createsNothing() {
        UUID rideId = UUID.randomUUID();
        when(processedEventGuard.alreadyProcessed(any())).thenReturn(false);
        when(paymentRepository.existsByRideIdAndType(rideId, FareType.CANCELLATION_FEE))
                .thenReturn(true);

        consumer.onRideEvent(cancelledEvent(rideId, "PASSENGER", "ACCEPTED"));

        verify(paymentRepository, never()).save(any());
    }

    // --- Helpers ------------------------------------------------------------

    private EventEnvelope<Object> completedEvent(UUID rideId, Double estimatedKm, Double actualKm,
                                                 Instant startedAt, Instant completedAt) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("rideId", rideId.toString());
        payload.put("passengerId", UUID.randomUUID().toString());
        payload.put("driverId", UUID.randomUUID().toString());
        payload.put("vehicleType", "CAR");
        payload.put("estimatedDistanceKm", estimatedKm);
        payload.put("actualDistanceKm", actualKm);
        payload.put("startedAt", startedAt.toString());
        payload.put("completedAt", completedAt.toString());
        payload.put("fareEstimateId", UUID.randomUUID().toString());
        payload.put("paymentMethod", "CARD");

        return new EventEnvelope<>(UUID.randomUUID(), EventTypes.RIDE_COMPLETED, 1,
                Instant.now(), UUID.randomUUID().toString(), payload);
    }

    private EventEnvelope<Object> cancelledEvent(UUID rideId, String cancelledBy, String previousStatus) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("rideId", rideId.toString());
        payload.put("passengerId", UUID.randomUUID().toString());
        payload.put("driverId", UUID.randomUUID().toString());
        payload.put("previousStatus", previousStatus);
        payload.put("cancelledBy", cancelledBy);
        payload.put("vehicleType", "CAR");
        payload.put("paymentMethod", "CARD");

        return new EventEnvelope<>(UUID.randomUUID(), EventTypes.RIDE_CANCELLED, 1,
                Instant.now(), UUID.randomUUID().toString(), payload);
    }

    private static RideLinkProperties.Tariff tariff(String base, String perKm, String perMin,
                                                    String minimum) {
        return new RideLinkProperties.Tariff(new BigDecimal(base), new BigDecimal(perKm),
                new BigDecimal(perMin), new BigDecimal(minimum));
    }
}
