package lk.ridelink.driver.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Record that an event has already been handled.
 *
 * <p>RabbitMQ guarantees at-least-once delivery, so every consumer will eventually see
 * the same message twice - after a broker restart, a redelivery, or a consumer crash
 * between doing the work and acknowledging. Inserting the event id in the same
 * transaction as the business change makes the whole handler idempotent: on a duplicate,
 * the primary key rejects the insert and the work is skipped.</p>
 */
@Entity
@Table(name = "processed_event")
public class ProcessedEvent {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "event_id", length = 36, nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedEvent() {
        // Required by JPA.
    }

    public ProcessedEvent(UUID eventId, String eventType) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.processedAt = Instant.now();
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
