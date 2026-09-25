package lk.ridelink.driver.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import lk.ridelink.driver.domain.DriverProfile;
import lk.ridelink.driver.domain.ServiceArea;
import lk.ridelink.driver.domain.Vehicle;
import lk.ridelink.driver.domain.VehicleType;
import lk.ridelink.driver.domain.VerificationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every way a driver can fail to qualify for going online, plus the licence-expiry
 * boundary from both sides.
 *
 * <p>Each failure reason is tested in isolation, because the endpoint's value is that it
 * returns the complete list of what to fix - a rule that silently stopped being reported
 * would leave a driver unable to work with no explanation.</p>
 */
class DriverEligibilityTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 28);

    @Test
    @DisplayName("a fully prepared driver has no unmet requirements")
    void reasonsNotEligible_fullyPreparedDriver_isEmpty() {
        DriverProfile driver = readyDriver();

        assertThat(DriverEligibility.reasonsNotEligible(driver, Optional.of(activeVehicle()), TODAY))
                .isEmpty();
    }

    @Test
    @DisplayName("an unverified driver is refused")
    void reasonsNotEligible_pendingVerification_isReported() {
        DriverProfile driver = readyDriver();
        driver.setVerificationStatus(VerificationStatus.PENDING);

        assertThat(DriverEligibility.reasonsNotEligible(driver, Optional.of(activeVehicle()), TODAY))
                .singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("verification status is PENDING");
    }

    @Test
    @DisplayName("a rejected driver is refused")
    void reasonsNotEligible_rejectedVerification_isReported() {
        DriverProfile driver = readyDriver();
        driver.setVerificationStatus(VerificationStatus.REJECTED);

        assertThat(DriverEligibility.reasonsNotEligible(driver, Optional.of(activeVehicle()), TODAY))
                .anyMatch(reason -> reason.contains("REJECTED"));
    }

    @Test
    @DisplayName("a suspended account is refused")
    void reasonsNotEligible_suspendedAccount_isReported() {
        DriverProfile driver = readyDriver();
        driver.setAccountActive(false);

        assertThat(DriverEligibility.reasonsNotEligible(driver, Optional.of(activeVehicle()), TODAY))
                .anyMatch(reason -> reason.contains("suspended or deactivated"));
    }

    @Test
    @DisplayName("an expired licence is refused")
    void reasonsNotEligible_expiredLicence_isReported() {
        DriverProfile driver = readyDriver();
        driver.updateDetails("B1234567", TODAY.minusDays(1), ServiceArea.NEGOMBO);

        assertThat(DriverEligibility.reasonsNotEligible(driver, Optional.of(activeVehicle()), TODAY))
                .anyMatch(reason -> reason.contains("Licence expired"));
    }

    @Test
    @DisplayName("a licence expiring today is still valid today (boundary)")
    void reasonsNotEligible_licenceExpiringToday_isStillValid() {
        DriverProfile driver = readyDriver();
        driver.updateDetails("B1234567", TODAY, ServiceArea.NEGOMBO);

        // The boundary case: a licence is valid through its expiry date, not up to the
        // day before it.
        assertThat(DriverEligibility.reasonsNotEligible(driver, Optional.of(activeVehicle()), TODAY))
                .isEmpty();
    }

    @Test
    @DisplayName("a missing licence expiry is refused with a distinct message")
    void reasonsNotEligible_noLicenceRecorded_isReported() {
        DriverProfile driver = DriverProfile.shellFor(UUID.randomUUID(), "Kamal Silva", "+94771234567");
        driver.setVerificationStatus(VerificationStatus.VERIFIED);
        driver.updateLocation(7.2083, 79.8358);

        assertThat(DriverEligibility.reasonsNotEligible(driver, Optional.of(activeVehicle()), TODAY))
                .anyMatch(reason -> reason.contains("No licence expiry recorded"));
    }

    @Test
    @DisplayName("no active vehicle is refused")
    void reasonsNotEligible_noActiveVehicle_isReported() {
        DriverProfile driver = readyDriver();

        assertThat(DriverEligibility.reasonsNotEligible(driver, Optional.empty(), TODAY))
                .anyMatch(reason -> reason.contains("No active vehicle"));
    }

    @Test
    @DisplayName("no location on file is refused")
    void reasonsNotEligible_noLocation_isReported() {
        DriverProfile driver = DriverProfile.shellFor(UUID.randomUUID(), "Kamal Silva", "+94771234567");
        driver.setVerificationStatus(VerificationStatus.VERIFIED);
        driver.updateDetails("B1234567", TODAY.plusYears(2), ServiceArea.NEGOMBO);

        // Without a position there is nothing to measure a pickup distance against, so
        // such a driver could never be matched anyway.
        assertThat(DriverEligibility.reasonsNotEligible(driver, Optional.of(activeVehicle()), TODAY))
                .anyMatch(reason -> reason.contains("No location set"));
    }

    @Test
    @DisplayName("a driver with no service area is refused")
    void reasonsNotEligible_noServiceArea_isReported() {
        DriverProfile driver = DriverProfile.shellFor(UUID.randomUUID(), "Kamal Silva", "+94771234567");
        driver.setVerificationStatus(VerificationStatus.VERIFIED);
        driver.updateLocation(7.2083, 79.8358);
        driver.updateDetails("B1234567", TODAY.plusYears(2), null);

        assertThat(DriverEligibility.reasonsNotEligible(driver, Optional.of(activeVehicle()), TODAY))
                .anyMatch(reason -> reason.contains("No service area"));
    }

    @Test
    @DisplayName("a brand-new driver gets every unmet requirement at once, not just the first")
    void reasonsNotEligible_brandNewDriver_reportsAllProblemsTogether() {
        DriverProfile driver = DriverProfile.shellFor(UUID.randomUUID(), "Kamal Silva", "+94771234567");

        // Reporting all of them is the whole point: otherwise the driver fixes one thing,
        // retries, and discovers the next problem, four times over.
        assertThat(DriverEligibility.reasonsNotEligible(driver, Optional.empty(), TODAY))
                .hasSize(5)
                .anyMatch(reason -> reason.contains("verification status"))
                .anyMatch(reason -> reason.contains("No licence expiry"))
                .anyMatch(reason -> reason.contains("No service area"))
                .anyMatch(reason -> reason.contains("No active vehicle"))
                .anyMatch(reason -> reason.contains("No location set"));
    }

    private static DriverProfile readyDriver() {
        DriverProfile driver = DriverProfile.shellFor(UUID.randomUUID(), "Kamal Silva", "+94771234567");
        driver.setVerificationStatus(VerificationStatus.VERIFIED);
        driver.updateDetails("B1234567", TODAY.plusYears(2), ServiceArea.NEGOMBO);
        driver.updateLocation(7.2083, 79.8358);
        return driver;
    }

    private static Vehicle activeVehicle() {
        Vehicle vehicle = Vehicle.register(UUID.randomUUID(), "WP CAB-1234", VehicleType.CAR,
                "Toyota", "Axio", "Silver", 4, 2018);
        vehicle.activate();
        return vehicle;
    }
}
