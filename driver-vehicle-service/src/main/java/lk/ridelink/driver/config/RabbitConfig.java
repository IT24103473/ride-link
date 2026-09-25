package lk.ridelink.driver.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology owned by the Driver &amp; Vehicle Service.
 *
 * <p>Each consuming service declares <em>its own</em> queues and bindings. The producer
 * declares only the exchange, so a new consumer can be added without touching, or
 * redeploying, the service that publishes.</p>
 *
 * <p>Every queue is paired with a dead-letter queue. Combined with
 * {@code default-requeue-rejected: false} and three retry attempts in
 * {@code application.yml}, a message that keeps failing lands in the DLQ for inspection
 * instead of looping forever and blocking everything behind it.</p>
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "ridelink.events";

    /** Dead letters go to a separate exchange so a DLQ can never re-trigger a consumer. */
    public static final String DLX = "ridelink.events.dlx";

    public static final String ACCOUNT_EVENTS_QUEUE = "driver.account-events";
    public static final String RIDE_EVENTS_QUEUE = "driver.ride-events";

    public static final String ROUTING_DRIVER_REGISTERED = "account.driver.registered";
    public static final String ROUTING_ACCOUNT_STATUS_CHANGED = "account.status.changed";
    public static final String ROUTING_RIDE_COMPLETED = "ride.completed";
    public static final String ROUTING_RIDE_CANCELLED = "ride.cancelled";

    @Bean
    public TopicExchange rideLinkEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(DLX, true, false);
    }

    // --- Account events -----------------------------------------------------

    @Bean
    public Queue accountEventsQueue() {
        return QueueBuilder.durable(ACCOUNT_EVENTS_QUEUE)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(ACCOUNT_EVENTS_QUEUE)
                .build();
    }

    @Bean
    public Queue accountEventsDlq() {
        return QueueBuilder.durable(ACCOUNT_EVENTS_QUEUE + ".dlq").build();
    }

    @Bean
    public Binding accountEventsDlqBinding() {
        return BindingBuilder.bind(accountEventsDlq()).to(deadLetterExchange()).with(ACCOUNT_EVENTS_QUEUE);
    }

    @Bean
    public Binding driverRegisteredBinding() {
        return BindingBuilder.bind(accountEventsQueue())
                .to(rideLinkEventsExchange())
                .with(ROUTING_DRIVER_REGISTERED);
    }

    @Bean
    public Binding accountStatusChangedBinding() {
        return BindingBuilder.bind(accountEventsQueue())
                .to(rideLinkEventsExchange())
                .with(ROUTING_ACCOUNT_STATUS_CHANGED);
    }

    // --- Ride events --------------------------------------------------------

    @Bean
    public Queue rideEventsQueue() {
        return QueueBuilder.durable(RIDE_EVENTS_QUEUE)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(RIDE_EVENTS_QUEUE)
                .build();
    }

    @Bean
    public Queue rideEventsDlq() {
        return QueueBuilder.durable(RIDE_EVENTS_QUEUE + ".dlq").build();
    }

    @Bean
    public Binding rideEventsDlqBinding() {
        return BindingBuilder.bind(rideEventsDlq()).to(deadLetterExchange()).with(RIDE_EVENTS_QUEUE);
    }

    @Bean
    public Binding rideCompletedBinding() {
        // A separate queue from the Payment service's: both receive every ride.completed,
        // which is exactly why the exchange is a topic rather than a direct exchange.
        return BindingBuilder.bind(rideEventsQueue())
                .to(rideLinkEventsExchange())
                .with(ROUTING_RIDE_COMPLETED);
    }

    @Bean
    public Binding rideCancelledBinding() {
        return BindingBuilder.bind(rideEventsQueue())
                .to(rideLinkEventsExchange())
                .with(ROUTING_RIDE_CANCELLED);
    }

    @Bean
    public MessageConverter jsonMessageConverter(com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
