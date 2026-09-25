package lk.ridelink.account.messaging;

import java.util.UUID;

/**
 * Payload of {@code account.driver.registered}.
 *
 * <p>Consumed by the Driver service to create a profile shell. It carries the name,
 * e-mail and phone so the Driver service never has to call back into Account just to
 * display a driver - the data is pushed once, at the moment it is created.</p>
 */
public record DriverRegisteredEvent(
        UUID accountId,
        String fullName,
        String email,
        String phone) {
}
