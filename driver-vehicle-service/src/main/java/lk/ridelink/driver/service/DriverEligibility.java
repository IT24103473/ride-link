package lk.ridelink.driver.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lk.ridelink.driver.domain.DriverProfile;
import lk.ridelink.driver.domain.Vehicle;
import lk.ridelink.driver.domain.VerificationStatus;

/**
 * Decides whether a driver may go online, and says why not when they may not.
 *
 * <p>Separated from the service that calls it for two reasons: the rule is pure logic
 * over a profile and a vehicle, so it is trivially unit-testable without mocks, and it
 * has exactly one reason to change - the eligibility policy - which keeps
 * {@code DriverServiceImpl} focused on orchestration.</p>
 *
 * <p>Every unmet requirement is collected rather than failing on the first, so the driver
 * sees the complete list in one response.</p>
 */
public final class DriverEligibility {

    private DriverEligibility() {
    }

    /**
     * @param today injected rather than read from the clock, so the "licence expires
     *              today" boundary can be tested deterministically
     * @return the unmet requirements; empty means the driver may go online
     */
    public static List<String> reasonsNotEligible(DriverProfile driver,
                                                  Optional<Vehicle> activeVehicle,
                                                  LocalDate today) {
        List<String> reasons = new ArrayList<>();

        if (driver.getVerificationStatus() != VerificationStatus.VERIFIED) {
            reasons.add("Driver verification status is " + driver.getVerificationStatus()
                    + "; an admin must verify the account first");
        }

        if (!driver.isAccountActive()) {
            reasons.add("The linked account is suspended or deactivated");
        }

        if (driver.getLicenceExpiry() == null) {
            reasons.add("No licence expiry recorded; update the profile first");
        } else if (!driver.isLicenceValid(today)) {
            reasons.add("Licence expired on " + driver.getLicenceExpiry());
        }

        if (driver.getServiceArea() == null) {
            reasons.add("No service area selected; update the profile first");
        }

        if (activeVehicle.isEmpty()) {
            reasons.add("No active vehicle; register a vehicle and activate it");
        }

        if (!driver.hasLocation()) {
            // Without a position there is nothing to measure a pickup distance against,
            // so such a driver could never be matched anyway.
            reasons.add("No location set; send a location update before going available");
        }

        return reasons;
    }
}
