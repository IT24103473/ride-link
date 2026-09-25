package lk.ridelink.ride.messaging;

/**
 * Publishes a domain event.
 *
 * <p>The ride service depends on this interface, never on {@code RabbitTemplate}, so the
 * ride rules can be unit-tested with a mock and carry no AMQP imports.</p>
 */
public interface EventPublisher {

    void publish(String routingKey, Object payload);
}
