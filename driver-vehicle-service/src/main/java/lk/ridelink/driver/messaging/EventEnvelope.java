package lk.ridelink.driver.messaging;

import java.time.Instant;
import java.util.UUID;
import lk.ridelink.driver.config.CorrelationIdFilter;

/**
 * The envelope every RideLink event is wrapped in. See {@code docs/contracts/events.md},
 * which is the source of truth for this shape.
 *
 * <p>Each service declares its own copy of this record. There is no shared module, so a
 * contract change has to be agreed and applied by every owner - which is the point:
 * it makes a breaking change visible rather than silent.</p>
 *
 * @param eventId       unique per publication; consumers persist it to stay idempotent
 * @param eventType     equal to the routing key, e.g. {@code account.driver.registered}
 * @param version       schema version; additive changes keep the same version
 * @param occurredAt    when the fact happened, not when it was published
 * @param correlationId carried from the originating HTTP request
 * @param payload       the event-specific body
 */
public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        int version,
        Instant occurredAt,
        String correlationId,
        T payload) {

    private static final int CURRENT_VERSION = 1;

    /** Builds an envelope, picking up the current request's correlation id automatically. */
    public static <T> EventEnvelope<T> of(String eventType, T payload) {
        return new EventEnvelope<>(
                UUID.randomUUID(),
                eventType,
                CURRENT_VERSION,
                Instant.now(),
                CorrelationIdFilter.current(),
                payload);
    }
}
