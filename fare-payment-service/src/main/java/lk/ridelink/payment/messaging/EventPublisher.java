package lk.ridelink.payment.messaging;

/**
 * Publishes a domain event.
 *
 * <p>{@code PaymentServiceImpl} depends on this interface rather than on
 * {@code RabbitTemplate}, so the payment rules can be unit-tested with a mock and carry no
 * AMQP imports. This is the Dependency Inversion example cited in the report.</p>
 */
public interface EventPublisher {

    void publish(String routingKey, Object payload);
}
