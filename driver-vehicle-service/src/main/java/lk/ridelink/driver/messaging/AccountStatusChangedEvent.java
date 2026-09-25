package lk.ridelink.driver.messaging;

import java.util.UUID;

/** Payload of {@code account.status.changed}. */
public record AccountStatusChangedEvent(
        UUID accountId,
        String role,
        String oldStatus,
        String newStatus) {

    private static final String ACTIVE = "ACTIVE";
    private static final String DRIVER = "DRIVER";

    /** Only driver accounts affect this service's data. */
    public boolean isDriverAccount() {
        return DRIVER.equalsIgnoreCase(role);
    }

    public boolean becameActive() {
        return ACTIVE.equalsIgnoreCase(newStatus);
    }
}
