package lk.ridelink.account.messaging;

/**
 * Routing keys this service publishes. Constants rather than inline strings, because a
 * typo in a routing key produces a message that is silently never delivered - the
 * hardest kind of bug to spot in a demo.
 */
public final class EventTypes {

    public static final String DRIVER_REGISTERED = "account.driver.registered";
    public static final String ACCOUNT_STATUS_CHANGED = "account.status.changed";

    private EventTypes() {
    }
}
