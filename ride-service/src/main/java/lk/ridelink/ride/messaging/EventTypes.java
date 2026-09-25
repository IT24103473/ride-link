package lk.ridelink.ride.messaging;

/** Routing keys this service publishes and consumes. */
public final class EventTypes {

    // Published
    public static final String RIDE_COMPLETED = "ride.completed";
    public static final String RIDE_CANCELLED = "ride.cancelled";

    // Consumed
    public static final String PAYMENT_SUCCEEDED = "payment.succeeded";
    public static final String PAYMENT_FAILED = "payment.failed";

    private EventTypes() {
    }
}
