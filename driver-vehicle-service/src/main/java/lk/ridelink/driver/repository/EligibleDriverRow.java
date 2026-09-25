package lk.ridelink.driver.repository;

import lk.ridelink.driver.domain.DriverProfile;
import lk.ridelink.driver.domain.Vehicle;

/**
 * One eligible driver together with the vehicle they are currently dispatched in.
 *
 * <p>The two are fetched in a single query because matching needs both - the profile for
 * position and waiting time, the vehicle for the type the passenger asked for - and
 * loading vehicles one at a time afterwards would be a classic N+1.</p>
 */
public record EligibleDriverRow(DriverProfile driver, Vehicle vehicle) {
}
