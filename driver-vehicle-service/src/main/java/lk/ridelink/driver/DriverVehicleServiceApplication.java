package lk.ridelink.driver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Driver &amp; Vehicle Service - owns driver supply.
 *
 * <p>Holds driver profiles, vehicles, availability and location in the
 * {@code ridelink_driver} database, and answers the Ride Service's driver-matching
 * queries over the internal API.</p>
 */
@SpringBootApplication
public class DriverVehicleServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DriverVehicleServiceApplication.class, args);
    }
}
