package lk.ridelink.driver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test: proves the Spring context wires up with no missing beans or
 * broken configuration. Cheap, but it catches most wiring mistakes immediately.
 */
@SpringBootTest
@ActiveProfiles("test")
class DriverVehicleServiceApplicationTests {

    @Test
    void contextLoads_withTestProfile_startsSuccessfully() {
        // The assertion is that the context above started without throwing.
    }
}
