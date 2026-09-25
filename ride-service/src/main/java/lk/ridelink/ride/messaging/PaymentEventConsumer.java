package lk.ridelink.ride.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import lk.ridelink.ride.config.CorrelationIdFilter;
import lk.ridelink.ride.config.RabbitConfig;
import lk.ridelink.ride.domain.PaymentStatus;
import lk.ridelink.ride.repository.RideRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the ride's {@code paymentStatus} read model in step with the Payment service.
 *
 * <p>Holding a copy means that reading a ride never has to call the Payment service, so a
 * passenger can see their ride history even while payments are down. The cost is that the
 * copy is momentarily stale after a payment - the usual trade of a runtime dependency for
 * eventual consistency, and the reason this field is documented as a read model rather
 * than the truth.</p>
 */
@Component
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);

    private final RideRepository rideRepository;
    private final ProcessedEventGuard processedEventGuard;
    private final ObjectMapper objectMapper;

    public PaymentEventConsumer(RideRepository rideRepository,
                                ProcessedEventGuard processedEventGuard,
                                ObjectMapper objectMapper) {
        this.rideRepository = rideRepository;
        this.processedEventGuard = processedEventGuard;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitConfig.PAYMENT_EVENTS_QUEUE)
    @Transactional
    public void onPaymentEvent(EventEnvelope<Object> envelope) {
        MDC.put(CorrelationIdFilter.MDC_KEY, String.valueOf(envelope.correlationId()));
        try {
            if (processedEventGuard.alreadyProcessed(envelope.eventId())) {
                return;
            }

            switch (envelope.eventType()) {
                case EventTypes.PAYMENT_SUCCEEDED -> {
                    PaymentSucceededEvent event = objectMapper.convertValue(
                            envelope.payload(), PaymentSucceededEvent.class);
                    applyStatus(event.rideId(), PaymentStatus.PAID,
                            "paid, receipt " + event.receiptNumber());
                }
                case EventTypes.PAYMENT_FAILED -> {
                    PaymentFailedEvent event = objectMapper.convertValue(
                            envelope.payload(), PaymentFailedEvent.class);
                    applyStatus(event.rideId(), PaymentStatus.FAILED, event.failureReason());
                }
                default -> log.warn("Ignoring unknown event type {}", envelope.eventType());
            }

            processedEventGuard.record(envelope.eventId(), envelope.eventType());
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }

    private void applyStatus(UUID rideId, PaymentStatus status, String detail) {
        rideRepository.findById(rideId).ifPresentOrElse(ride -> {
            // A failure arriving after a successful retry must not undo the PAID state.
            // Payment failure is not terminal, so ordering here is not guaranteed.
            if (ride.getPaymentStatus() == PaymentStatus.PAID && status == PaymentStatus.FAILED) {
                log.info("Ignoring payment.failed for ride {}: it is already PAID", rideId);
                return;
            }

            ride.updatePaymentStatus(status);
            rideRepository.save(ride);
            log.info("Ride {} paymentStatus set to {} ({})", rideId, status, detail);

        }, () -> log.warn("Payment event referenced unknown ride {}; ignoring", rideId));
    }
}
