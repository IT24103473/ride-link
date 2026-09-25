package lk.ridelink.payment;

import lk.ridelink.payment.config.RideLinkProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Fare &amp; Payment Service - owns money.
 *
 * <p>Calculates fare estimates and final fares from the tariff table and runs the
 * simulated payment flow, storing everything in the {@code ridelink_payment} database.
 * No real payment provider is contacted; card tokens are fixed placeholders.</p>
 */
@SpringBootApplication
@EnableConfigurationProperties(RideLinkProperties.class)
public class FarePaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FarePaymentServiceApplication.class, args);
    }
}
