package lk.ridelink.driver.messaging;

import java.util.UUID;

/**
 * Payload of {@code account.driver.registered}, as published by the Account Service.
 *
 * <p>This is a local copy of the contract in {@code docs/contracts/events.md}, not a
 * shared class. Unknown fields are ignored on deserialisation, so the producer can add
 * fields without breaking this consumer.</p>
 */
public record DriverRegisteredEvent(
        UUID accountId,
        String fullName,
        String email,
        String phone) {
}
