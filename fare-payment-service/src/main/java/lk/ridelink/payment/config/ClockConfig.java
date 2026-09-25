package lk.ridelink.payment.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Makes the system clock an injectable dependency.
 *
 * <p>Time matters here in ways it does not elsewhere: estimates expire, receipts are
 * numbered per calendar year, and fares depend on elapsed journey time. Injecting a
 * {@link Clock} lets those rules be tested at a fixed instant instead of with sleeps or
 * tolerances, which is the difference between a deterministic test and a flaky one.</p>
 */
@Configuration
public class ClockConfig {

    /** UTC everywhere: all RideLink timestamps are stored and compared in UTC. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
