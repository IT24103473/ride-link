package lk.ridelink.driver.messaging;

import java.util.UUID;
import lk.ridelink.driver.domain.ProcessedEvent;
import lk.ridelink.driver.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Makes consumers idempotent by remembering which event ids have been handled.
 *
 * <p>RabbitMQ delivers at least once, so every consumer will eventually see the same
 * message twice - after a redelivery, a broker restart, or a consumer that did the work
 * but crashed before acknowledging. Without a guard, that would release a driver twice or
 * double-count a completed trip.</p>
 *
 * <p>Two layers of protection, because the cheap one is not airtight:</p>
 * <ol>
 *   <li>{@link #alreadyProcessed} catches the ordinary redelivery with a fast primary-key
 *       lookup.</li>
 *   <li>{@link #record} inserts the id <em>inside the handler's transaction</em>. If two
 *       deliveries are processed concurrently, the second insert violates the primary key
 *       and its whole transaction rolls back - so the duplicate cannot half-apply.</li>
 * </ol>
 */
@Component
public class ProcessedEventGuard {

    private static final Logger log = LoggerFactory.getLogger(ProcessedEventGuard.class);

    private final ProcessedEventRepository processedEventRepository;

    public ProcessedEventGuard(ProcessedEventRepository processedEventRepository) {
        this.processedEventRepository = processedEventRepository;
    }

    /** True when this event has been handled before and should be skipped. */
    public boolean alreadyProcessed(UUID eventId) {
        boolean seen = processedEventRepository.existsById(eventId);
        if (seen) {
            log.info("Skipping event {}: already processed", eventId);
        }
        return seen;
    }

    /**
     * Marks the event handled. Must be called inside the same transaction as the business
     * change, so the two either both commit or both roll back.
     */
    public void record(UUID eventId, String eventType) {
        processedEventRepository.save(new ProcessedEvent(eventId, eventType));
    }
}
