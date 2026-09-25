package lk.ridelink.ride.config;

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
 * RabbitMQ topology owned by the Ride Management Service.
 *
 * <p>This service publishes ride events and consumes payment events, so it declares the
 * exchange plus its own payment queue. It does <em>not</em> declare the queues that
 * consume its ride events - those belong to the Driver and Payment services, which is what
 * lets a new consumer be added without redeploying this one.</p>
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "ridelink.events";
    public static final String DLX = "ridelink.events.dlx";

    public static final String PAYMENT_EVENTS_QUEUE = "ride.payment-events";

    public static final String ROUTING_PAYMENT_SUCCEEDED = "payment.succeeded";
    public static final String ROUTING_PAYMENT_FAILED = "payment.failed";

    @Bean
    public TopicExchange rideLinkEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(DLX, true, false);
    }

    @Bean
    public Queue paymentEventsQueue() {
        return QueueBuilder.durable(PAYMENT_EVENTS_QUEUE)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(PAYMENT_EVENTS_QUEUE)
                .build();
    }

    @Bean
    public Queue paymentEventsDlq() {
        return QueueBuilder.durable(PAYMENT_EVENTS_QUEUE + ".dlq").build();
    }

    @Bean
    public Binding paymentEventsDlqBinding() {
        return BindingBuilder.bind(paymentEventsDlq()).to(deadLetterExchange()).with(PAYMENT_EVENTS_QUEUE);
    }

    @Bean
    public Binding paymentSucceededBinding() {
        return BindingBuilder.bind(paymentEventsQueue())
                .to(rideLinkEventsExchange())
                .with(ROUTING_PAYMENT_SUCCEEDED);
    }

    @Bean
    public Binding paymentFailedBinding() {
        return BindingBuilder.bind(paymentEventsQueue())
                .to(rideLinkEventsExchange())
                .with(ROUTING_PAYMENT_FAILED);
    }

    @Bean
    public MessageConverter jsonMessageConverter(com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
