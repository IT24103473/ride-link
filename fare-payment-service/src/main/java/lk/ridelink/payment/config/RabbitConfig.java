package lk.ridelink.payment.config;

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
 * RabbitMQ topology owned by the Fare &amp; Payment Service.
 *
 * <p>This service both consumes (ride events) and publishes (payment events). Its ride
 * queue is separate from the Driver service's even though both bind to the same routing
 * keys - that is precisely what a topic exchange is for, and it means each consumer can
 * fail, retry and dead-letter independently.</p>
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "ridelink.events";
    public static final String DLX = "ridelink.events.dlx";

    public static final String RIDE_EVENTS_QUEUE = "payment.ride-events";

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

    @Bean
    public Queue rideEventsQueue() {
        return QueueBuilder.durable(RIDE_EVENTS_QUEUE)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(RIDE_EVENTS_QUEUE)
                .build();
    }

    @Bean
    public Queue rideEventsDlq() {
        // A message that fails three times lands here rather than looping forever and
        // blocking every payment behind it.
        return QueueBuilder.durable(RIDE_EVENTS_QUEUE + ".dlq").build();
    }

    @Bean
    public Binding rideEventsDlqBinding() {
        return BindingBuilder.bind(rideEventsDlq()).to(deadLetterExchange()).with(RIDE_EVENTS_QUEUE);
    }

    @Bean
    public Binding rideCompletedBinding() {
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
