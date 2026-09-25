package lk.ridelink.ride;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ride Management Service - owns the trip lifecycle.
 *
 * <p>Orchestrates a ride from request to completion in the {@code ridelink_ride}
 * database: it calls Fare &amp; Payment for an estimate and Driver &amp; Vehicle for
 * assignment, and publishes ride events for the other services to react to.</p>
 */
@SpringBootApplication
public class RideServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RideServiceApplication.class, args);
    }
}
