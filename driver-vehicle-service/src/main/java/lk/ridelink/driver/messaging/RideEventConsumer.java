package lk.ridelink.driver.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import lk.ridelink.driver.config.CorrelationIdFilter;
import lk.ridelink.driver.config.RabbitConfig;
import lk.ridelink.driver.domain.DriverProfile;
import lk.ridelink.driver.repository.DriverProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Frees drivers when their ride ends.
 *
 * <p>This is why ride completion is an event rather than a synchronous call: the driver's
 * "complete" request must not fail, or hang, because this service is slow or restarting.
 * The driver is released a moment later, which is perfectly acceptable - nobody is waiting
 * on it.</p>
 */
@Component
public class RideEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(RideEventConsumer.class);

    private final DriverProfileRepository driverRepository;
    private final ProcessedEventGuard processedEventGuard;
    private final ObjectMapper objectMapper;

    public RideEventConsumer(DriverProfileRepository driverRepository,
                             ProcessedEventGuard processedEventGuard,
                             ObjectMapper objectMapper) {
        this.driverRepository = driverRepository;
        this.processedEventGuard = processedEventGuard;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitConfig.RIDE_EVENTS_QUEUE)
    @Transactional
    public void onRideEvent(EventEnvelope<Object> envelope) {
        MDC.put(CorrelationIdFilter.MDC_KEY, String.valueOf(envelope.correlationId()));
        try {
            if (processedEventGuard.alreadyProcessed(envelope.eventId())) {
                return;
            }

            switch (envelope.eventType()) {
                case RabbitConfig.ROUTING_RIDE_COMPLETED -> handleRideCompleted(
                        objectMapper.convertValue(envelope.payload(), RideCompletedEvent.class));
                case RabbitConfig.ROUTING_RIDE_CANCELLED -> handleRideCancelled(
                        objectMapper.convertValue(envelope.payload(), RideCancelledEvent.class));
                default -> log.warn("Ignoring unknown event type {}", envelope.eventType());
            }

            processedEventGuard.record(envelope.eventId(), envelope.eventType());
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }

    /** Releases the driver and credits the completed trip. */
    private void handleRideCompleted(RideCompletedEvent event) {
        releaseIfOnRide(event.driverId(), event.rideId(), driver -> {
            driver.release();
            // Counted here rather than when the ride starts, so an abandoned trip never
            // inflates a driver's record.
            driver.incrementCompletedTrips();
            log.info("Driver {} released after completing ride {} (total trips {})",
                    driver.getDriverId(), event.rideId(), driver.getCompletedTrips());
        });
    }

    /** Releases the driver without crediting a trip, since none was made. */
    private void handleRideCancelled(RideCancelledEvent event) {
        if (event.driverId() == null) {
            // Cancelled while still REQUESTED: nobody had been reserved.
            log.debug("Ride {} cancelled before assignment; no driver to release", event.rideId());
            return;
        }

        releaseIfOnRide(event.driverId(), event.rideId(), driver -> {
            driver.release();
            log.info("Driver {} released after ride {} was cancelled", driver.getDriverId(), event.rideId());
        });
    }

    /**
     * Applies a release only when the driver is currently on <em>this</em> ride.
     *
     * <p>This guard is what makes redelivery safe in the way that matters: a late or
     * duplicated event for an old ride must not free a driver who has since been reserved
     * for a new one.</p>
     */
    private void releaseIfOnRide(UUID driverId, UUID rideId, java.util.function.Consumer<DriverProfile> action) {
        driverRepository.findById(driverId).ifPresentOrElse(driver -> {
            if (!driver.isReservedFor(rideId)) {
                log.debug("Driver {} is not on ride {} (currently {}); nothing to release",
                        driverId, rideId, driver.getCurrentRideId());
                return;
            }
            action.accept(driver);
            driverRepository.save(driver);
        }, () -> log.warn("Ride event referenced unknown driver {}; ignoring", driverId));
    }
}
