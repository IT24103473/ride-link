package lk.ridelink.ride.messaging;

import lk.ridelink.ride.config.CorrelationIdFilter;
import lk.ridelink.ride.config.RabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ implementation of {@link EventPublisher}. The only class in this service that
 * knows AMQP exists.
 */
@Component
public class RabbitEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public RabbitEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publish(String routingKey, Object payload) {
        EventEnvelope<Object> envelope = EventEnvelope.of(routingKey, payload);

        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, routingKey, envelope, message -> {
            // Also a header, so the id can be read in the management UI without
            // deserialising the body.
            message.getMessageProperties().setHeader(CorrelationIdFilter.HEADER, envelope.correlationId());
            return message;
        });

        log.info("Published {} eventId={} correlationId={}",
                routingKey, envelope.eventId(), envelope.correlationId());
    }
}
