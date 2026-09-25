package lk.ridelink.account.messaging;

import java.util.UUID;

/**
 * Payload of {@code account.status.changed}.
 *
 * <p>Includes {@code role} so consumers can ignore events for accounts they do not care
 * about without a lookup, and {@code oldStatus} so a consumer can tell a reactivation
 * from a no-op redelivery.</p>
 */
public record AccountStatusChangedEvent(
        UUID accountId,
        String role,
        String oldStatus,
        String newStatus) {
}
