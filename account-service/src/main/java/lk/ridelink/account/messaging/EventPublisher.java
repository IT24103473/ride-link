package lk.ridelink.account.messaging;

/**
 * Publishes a domain event.
 *
 * <p>The business services depend on this interface, never on {@code RabbitTemplate}.
 * Two payoffs: the services carry no AMQP imports and can be unit-tested with a mock,
 * and swapping RabbitMQ for another transport would touch one class. This is the
 * Dependency Inversion example cited in the report.</p>
 */
public interface EventPublisher {

    /**
     * @param routingKey the event type, e.g. {@code account.driver.registered}
     * @param payload    the event body; wrapped in an {@link EventEnvelope} by the impl
     */
    void publish(String routingKey, Object payload);
}
