package lk.ridelink.driver.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import lk.ridelink.driver.config.CorrelationIdFilter;
import lk.ridelink.driver.config.RabbitConfig;
import lk.ridelink.driver.domain.DriverProfile;
import lk.ridelink.driver.repository.DriverProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes account events so this service learns about drivers without polling Account.
 *
 * <p>Both handlers are idempotent, which is required rather than merely nice: the broker
 * guarantees at-least-once delivery.</p>
 */
@Component
public class AccountEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AccountEventConsumer.class);

    private final DriverProfileRepository driverRepository;
    private final ProcessedEventGuard processedEventGuard;
    private final ObjectMapper objectMapper;

    public AccountEventConsumer(DriverProfileRepository driverRepository,
                                ProcessedEventGuard processedEventGuard,
                                ObjectMapper objectMapper) {
        this.driverRepository = driverRepository;
        this.processedEventGuard = processedEventGuard;
        this.objectMapper = objectMapper;
    }

    /**
     * Single listener for the queue, dispatching on event type.
     *
     * <p>One method per queue rather than per event type, because the queue carries both
     * routing keys and Spring AMQP cannot dispatch on the routing key without a typed
     * converter that would couple this service to the producer's class names.</p>
     */
    @RabbitListener(queues = RabbitConfig.ACCOUNT_EVENTS_QUEUE)
    @Transactional
    public void onAccountEvent(EventEnvelope<Object> envelope) {
        // Carry the publisher's correlation id into this consumer's logs, so one
        // passenger action stays traceable across services and across the broker.
        MDC.put(CorrelationIdFilter.MDC_KEY, String.valueOf(envelope.correlationId()));
        try {
            if (processedEventGuard.alreadyProcessed(envelope.eventId())) {
                return;
            }

            switch (envelope.eventType()) {
                case RabbitConfig.ROUTING_DRIVER_REGISTERED -> handleDriverRegistered(
                        objectMapper.convertValue(envelope.payload(), DriverRegisteredEvent.class));
                case RabbitConfig.ROUTING_ACCOUNT_STATUS_CHANGED -> handleStatusChanged(
                        objectMapper.convertValue(envelope.payload(), AccountStatusChangedEvent.class));
                // An unrecognised type is acknowledged rather than dead-lettered: it means
                // a producer added an event this service does not care about yet.
                default -> log.warn("Ignoring unknown event type {}", envelope.eventType());
            }

            processedEventGuard.record(envelope.eventId(), envelope.eventType());
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }

    /**
     * Creates the profile shell for a newly registered driver, so they can complete their
     * details the moment they first log in.
     */
    private void handleDriverRegistered(DriverRegisteredEvent event) {
        UUID driverId = event.accountId();

        // Belt and braces alongside the ProcessedEvent guard: if the same driver were
        // ever announced under two different event ids, this still prevents overwriting an
        // established profile with a blank shell.
        if (driverRepository.existsById(driverId)) {
            log.info("Driver profile {} already exists; ignoring registration event", driverId);
            return;
        }

        driverRepository.save(DriverProfile.shellFor(driverId, event.fullName(), event.phone()));
        log.info("Created PENDING driver profile {} from registration event", driverId);
    }

    /**
     * Mirrors the account's status locally. Without this, a suspended driver would keep
     * being matched until someone noticed, because matching never calls Account.
     */
    private void handleStatusChanged(AccountStatusChangedEvent event) {
        if (!event.isDriverAccount()) {
            // Passenger and admin status changes are irrelevant here.
            return;
        }

        driverRepository.findById(event.accountId()).ifPresentOrElse(driver -> {
            // setAccountActive also forces an AVAILABLE driver OFFLINE, so a suspension
            // takes them out of dispatch immediately.
            driver.setAccountActive(event.becameActive());
            driverRepository.save(driver);
            log.info("Driver {} accountActive set to {} ({} -> {})",
                    event.accountId(), event.becameActive(), event.oldStatus(), event.newStatus());
        }, () -> log.warn("Status change for unknown driver {}; ignoring", event.accountId()));
    }
}
