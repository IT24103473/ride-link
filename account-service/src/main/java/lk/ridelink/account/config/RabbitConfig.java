package lk.ridelink.account.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ wiring for the Account Service.
 *
 * <p>This service only publishes, so it declares the exchange but no queues - a
 * publisher should not have to know who listens. The consuming services declare and
 * bind their own queues, which is what lets a new consumer be added without touching
 * the producer.</p>
 */
@Configuration
public class RabbitConfig {

    /** Shared by all four services; see docs/contracts/events.md. */
    public static final String EXCHANGE = "ridelink.events";

    /**
     * Topic (not fanout or direct) so consumers can bind by pattern and so one event can
     * reach several queues. Durable, so the exchange survives a broker restart.
     */
    @Bean
    public TopicExchange rideLinkEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    /**
     * JSON on the wire rather than Java serialisation: the four services must stay
     * independently deployable, and JSON keeps them from sharing compiled classes.
     */
    @Bean
    public MessageConverter jsonMessageConverter(com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
