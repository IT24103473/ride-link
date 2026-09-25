package lk.ridelink.payment.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import lk.ridelink.payment.config.CorrelationIdFilter;
import lk.ridelink.payment.config.RabbitConfig;
import lk.ridelink.payment.config.RideLinkProperties;
import lk.ridelink.payment.domain.FareBreakdown;
import lk.ridelink.payment.domain.FareCalculator;
import lk.ridelink.payment.domain.FareType;
import lk.ridelink.payment.domain.FinalFare;
import lk.ridelink.payment.domain.Payment;
import lk.ridelink.payment.domain.PaymentMethod;
import lk.ridelink.payment.domain.VehicleType;
import lk.ridelink.payment.repository.FinalFareRepository;
import lk.ridelink.payment.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns finished rides into money owed.
 *
 * <p>Driven by events rather than by a synchronous call from the Ride service, so a
 * driver's "complete" request never fails or hangs because this service is slow. The
 * trade-off is eventual consistency: for a moment after completion,
 * {@code GET /payments/rides/{rideId}} returns 404 PAYMENT_NOT_READY, and clients poll.
 * That is why the code is distinct from a plain NOT_FOUND.</p>
 */
@Component
public class RideEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(RideEventConsumer.class);

    /** A sub-minute trip is still billed one minute of time. */
    private static final int MINIMUM_DURATION_MIN = 1;

    private final FinalFareRepository finalFareRepository;
    private final PaymentRepository paymentRepository;
    private final FareCalculator fareCalculator;
    private final ProcessedEventGuard processedEventGuard;
    private final RideLinkProperties properties;
    private final ObjectMapper objectMapper;

    public RideEventConsumer(FinalFareRepository finalFareRepository,
                             PaymentRepository paymentRepository,
                             FareCalculator fareCalculator,
                             ProcessedEventGuard processedEventGuard,
                             RideLinkProperties properties,
                             ObjectMapper objectMapper) {
        this.finalFareRepository = finalFareRepository;
        this.paymentRepository = paymentRepository;
        this.fareCalculator = fareCalculator;
        this.processedEventGuard = processedEventGuard;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitConfig.RIDE_EVENTS_QUEUE)
    @Transactional
    public void onRideEvent(EventEnvelope<Object> envelope) {
        MDC.put(CorrelationIdFilter.MDC_KEY, String.valueOf(envelope.correlationId()));
        try {
            if (processedEventGuard.alreadyProcessed(envelope.eventId())) {
                return;
            }

            switch (envelope.eventType()) {
                case EventTypes.RIDE_COMPLETED -> handleRideCompleted(
                        objectMapper.convertValue(envelope.payload(), RideCompletedEvent.class));
                case EventTypes.RIDE_CANCELLED -> handleRideCancelled(
                        objectMapper.convertValue(envelope.payload(), RideCancelledEvent.class));
                default -> log.warn("Ignoring unknown event type {}", envelope.eventType());
            }

            processedEventGuard.record(envelope.eventId(), envelope.eventType());
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }

    /** Prices the completed journey and raises a PENDING payment for it. */
    private void handleRideCompleted(RideCompletedEvent event) {
        // Second line of defence behind the (ride_id, type) unique index: a redelivery
        // must never produce a second charge for the same trip.
        if (paymentRepository.existsByRideIdAndType(event.rideId(), FareType.TRIP)) {
            log.info("Trip fare for ride {} already exists; ignoring duplicate event", event.rideId());
            return;
        }

        VehicleType vehicleType = VehicleType.valueOf(event.vehicleType());

        // The driver's reported distance is preferred; the estimate is the fallback when
        // they did not report one.
        double distanceKm = event.actualDistanceKm() != null
                ? event.actualDistanceKm()
                : nullSafe(event.estimatedDistanceKm());

        int durationMin = actualDurationMin(event.startedAt(), event.completedAt());

        FareBreakdown breakdown = fareCalculator.calculate(
                vehicleType, BigDecimal.valueOf(distanceKm), durationMin);

        FinalFare fare = FinalFare.forTrip(event.rideId(), vehicleType, breakdown,
                properties.fare().currency());
        finalFareRepository.save(fare);

        Payment payment = Payment.pending(event.rideId(), event.passengerId(), event.driverId(),
                fare.getId(), FareType.TRIP, breakdown.total(), properties.fare().currency(),
                PaymentMethod.valueOf(event.paymentMethod()));
        paymentRepository.save(payment);

        log.info("Final fare {} for ride {}: {} {} ({}km, {}min) -> payment {} PENDING",
                fare.getId(), event.rideId(), breakdown.total(), properties.fare().currency(),
                breakdown.distanceKm(), durationMin, payment.getId());
    }

    /**
     * Charges the flat cancellation fee, but only where the documented rule says it is due.
     *
     * <p>When no fee applies, nothing at all is created - so
     * {@code GET /payments/rides/{rideId}} stays 404 for that ride, which is the correct
     * answer: there is nothing to pay.</p>
     */
    private void handleRideCancelled(RideCancelledEvent event) {
        if (!event.incursCancellationFee()) {
            log.info("Ride {} cancelled by {} from {}; no fee due",
                    event.rideId(), event.cancelledBy(), event.previousStatus());
            return;
        }

        if (paymentRepository.existsByRideIdAndType(event.rideId(), FareType.CANCELLATION_FEE)) {
            log.info("Cancellation fee for ride {} already exists; ignoring duplicate", event.rideId());
            return;
        }

        BigDecimal fee = properties.fare().cancellationFee();
        VehicleType vehicleType = VehicleType.valueOf(event.vehicleType());

        // A flat fee, so the breakdown is not a calculated fare: zero distance and zero
        // time, with the fee itself as both the minimum and the total. Recording it in the
        // same shape as a trip fare keeps the receipt format uniform.
        FareBreakdown breakdown = new FareBreakdown(
                BigDecimal.ZERO.setScale(2), 0,
                BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2), fee, true, fee);

        FinalFare fare = FinalFare.forCancellation(event.rideId(), vehicleType, breakdown,
                properties.fare().currency());
        finalFareRepository.save(fare);

        Payment payment = Payment.pending(event.rideId(), event.passengerId(), event.driverId(),
                fare.getId(), FareType.CANCELLATION_FEE, fee, properties.fare().currency(),
                PaymentMethod.valueOf(event.paymentMethod()));
        paymentRepository.save(payment);

        log.info("Cancellation fee {} {} charged for ride {} (payment {})",
                fee, properties.fare().currency(), event.rideId(), payment.getId());
    }

    /**
     * Real elapsed journey time in whole minutes, rounded up, never below one.
     *
     * <p>Rounding up means a trip is never billed for less time than it took; the floor
     * stops a 40-second journey being charged nothing for time.</p>
     */
    private static int actualDurationMin(Instant startedAt, Instant completedAt) {
        if (startedAt == null || completedAt == null || completedAt.isBefore(startedAt)) {
            // Should not happen, but a clock skew must not produce a negative charge.
            return MINIMUM_DURATION_MIN;
        }

        long seconds = Duration.between(startedAt, completedAt).toSeconds();
        long minutes = (seconds + 59) / 60;

        return (int) Math.max(MINIMUM_DURATION_MIN, minutes);
    }

    private static double nullSafe(Double value) {
        return value == null ? 0.0 : value;
    }
}
