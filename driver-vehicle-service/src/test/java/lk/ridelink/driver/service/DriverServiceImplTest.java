package lk.ridelink.driver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import lk.ridelink.driver.domain.Availability;
import lk.ridelink.driver.domain.DriverProfile;
import lk.ridelink.driver.domain.ServiceArea;
import lk.ridelink.driver.domain.Vehicle;
import lk.ridelink.driver.domain.VehicleType;
import lk.ridelink.driver.domain.VerificationStatus;
import lk.ridelink.driver.dto.AvailabilityRequest;
import lk.ridelink.driver.dto.DriverProfileResponse;
import lk.ridelink.driver.dto.DriverSummaryResponse;
import lk.ridelink.driver.dto.LocationRequest;
import lk.ridelink.driver.dto.VerificationRequest;
import lk.ridelink.driver.exception.BusinessRuleException;
import lk.ridelink.driver.exception.DriverBusyException;
import lk.ridelink.driver.exception.DriverNotEligibleException;
import lk.ridelink.driver.exception.NotFoundException;
import lk.ridelink.driver.mapper.DriverMapper;
import lk.ridelink.driver.repository.DriverProfileRepository;
import lk.ridelink.driver.repository.VehicleRepository;
import lk.ridelink.driver.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Availability transitions, verification decisions and the public-summary boundary. */
@ExtendWith(MockitoExtension.class)
class DriverServiceImplTest {

    @Mock
    private DriverProfileRepository driverRepository;
    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private CurrentUser currentUser;

    private DriverServiceImpl driverService;

    @BeforeEach
    void setUp() {
        driverService = new DriverServiceImpl(driverRepository, vehicleRepository,
                new DriverMapper(), currentUser);
    }

    // --- Going available ----------------------------------------------------

    @Test
    @DisplayName("a fully prepared driver can go AVAILABLE and availableSince is stamped")
    void changeOwnAvailability_eligibleDriver_goesAvailable() {
        DriverProfile driver = readyDriver();
        when(currentUser.id()).thenReturn(driver.getDriverId());
        when(driverRepository.findById(driver.getDriverId())).thenReturn(Optional.of(driver));
        when(vehicleRepository.findByDriverIdAndActiveTrue(driver.getDriverId()))
                .thenReturn(Optional.of(activeVehicle(driver.getDriverId())));

        DriverProfileResponse response = driverService.changeOwnAvailability(
                new AvailabilityRequest(Availability.AVAILABLE));

        assertThat(response.availability()).isEqualTo(Availability.AVAILABLE);
        // The ranking tie-break depends on this timestamp being set here.
        assertThat(response.availableSince()).isNotNull();
    }

    @Test
    @DisplayName("an unverified driver is refused with the reasons listed")
    void changeOwnAvailability_notVerified_throwsWithReasons() {
        DriverProfile driver = readyDriver();
        driver.setVerificationStatus(VerificationStatus.PENDING);
        when(currentUser.id()).thenReturn(driver.getDriverId());
        when(driverRepository.findById(driver.getDriverId())).thenReturn(Optional.of(driver));
        when(vehicleRepository.findByDriverIdAndActiveTrue(driver.getDriverId()))
                .thenReturn(Optional.of(activeVehicle(driver.getDriverId())));

        assertThatThrownBy(() -> driverService.changeOwnAvailability(
                new AvailabilityRequest(Availability.AVAILABLE)))
                .isInstanceOf(DriverNotEligibleException.class)
                .extracting(ex -> ((DriverNotEligibleException) ex).getReasons())
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.list(String.class))
                .anyMatch(reason -> reason.contains("verification status"));

        assertThat(driver.getAvailability()).isEqualTo(Availability.OFFLINE);
        verify(driverRepository, never()).save(any());
    }

    @Test
    @DisplayName("a driver with no active vehicle is refused")
    void changeOwnAvailability_noActiveVehicle_throwsNotEligible() {
        DriverProfile driver = readyDriver();
        when(currentUser.id()).thenReturn(driver.getDriverId());
        when(driverRepository.findById(driver.getDriverId())).thenReturn(Optional.of(driver));
        when(vehicleRepository.findByDriverIdAndActiveTrue(driver.getDriverId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> driverService.changeOwnAvailability(
                new AvailabilityRequest(Availability.AVAILABLE)))
                .isInstanceOf(DriverNotEligibleException.class);
    }

    // --- Going offline ------------------------------------------------------

    @Test
    @DisplayName("an available driver can go OFFLINE")
    void changeOwnAvailability_availableDriver_goesOffline() {
        DriverProfile driver = readyDriver();
        driver.goAvailable();
        when(currentUser.id()).thenReturn(driver.getDriverId());
        when(driverRepository.findById(driver.getDriverId())).thenReturn(Optional.of(driver));
        when(vehicleRepository.findByDriverIdAndActiveTrue(driver.getDriverId()))
                .thenReturn(Optional.of(activeVehicle(driver.getDriverId())));

        DriverProfileResponse response = driverService.changeOwnAvailability(
                new AvailabilityRequest(Availability.OFFLINE));

        assertThat(response.availability()).isEqualTo(Availability.OFFLINE);
    }

    @Test
    @DisplayName("a driver on a ride cannot go OFFLINE")
    void changeOwnAvailability_busyDriver_throwsDriverBusy() {
        DriverProfile driver = readyDriver();
        driver.goAvailable();
        driver.reserveFor(UUID.randomUUID());
        when(currentUser.id()).thenReturn(driver.getDriverId());
        when(driverRepository.findById(driver.getDriverId())).thenReturn(Optional.of(driver));

        // Otherwise a driver could abandon a passenger mid-trip and vanish from the system.
        assertThatThrownBy(() -> driverService.changeOwnAvailability(
                new AvailabilityRequest(Availability.OFFLINE)))
                .isInstanceOf(DriverBusyException.class);

        assertThat(driver.getAvailability()).isEqualTo(Availability.BUSY);
    }

    @Test
    @DisplayName("BUSY cannot be set through the API")
    void changeOwnAvailability_requestingBusy_isRejected() {
        DriverProfile driver = readyDriver();
        when(currentUser.id()).thenReturn(driver.getDriverId());
        when(driverRepository.findById(driver.getDriverId())).thenReturn(Optional.of(driver));

        // BUSY is entered only by a reservation from the Ride service; allowing it here
        // would let a driver fake being on a trip to dodge dispatch.
        assertThatThrownBy(() -> driverService.changeOwnAvailability(
                new AvailabilityRequest(Availability.BUSY)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot be set manually");
    }

    // --- Location -----------------------------------------------------------

    @Test
    @DisplayName("a location update records the position and stamps the time")
    void updateOwnLocation_validCoordinates_storesPositionAndTimestamp() {
        DriverProfile driver = readyDriver();
        when(currentUser.id()).thenReturn(driver.getDriverId());
        when(driverRepository.findById(driver.getDriverId())).thenReturn(Optional.of(driver));
        when(vehicleRepository.findByDriverIdAndActiveTrue(driver.getDriverId()))
                .thenReturn(Optional.empty());

        DriverProfileResponse response = driverService.updateOwnLocation(
                new LocationRequest(6.9344, 79.8428));

        assertThat(response.currentLat()).isEqualTo(6.9344);
        assertThat(response.currentLng()).isEqualTo(79.8428);
        // The timestamp is what the 30-minute freshness filter reads.
        assertThat(response.locationUpdatedAt()).isNotNull();
    }

    // --- Verification -------------------------------------------------------

    @Test
    @DisplayName("an admin can verify a driver")
    void changeVerification_toVerified_updatesStatus() {
        DriverProfile driver = readyDriver();
        driver.setVerificationStatus(VerificationStatus.PENDING);
        when(driverRepository.findById(driver.getDriverId())).thenReturn(Optional.of(driver));
        when(vehicleRepository.findByDriverIdAndActiveTrue(driver.getDriverId()))
                .thenReturn(Optional.empty());

        DriverProfileResponse response = driverService.changeVerification(
                driver.getDriverId(), new VerificationRequest(VerificationStatus.VERIFIED));

        assertThat(response.verificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
    }

    @Test
    @DisplayName("rejecting an online driver forces them offline immediately")
    void changeVerification_rejectingAvailableDriver_forcesOffline() {
        DriverProfile driver = readyDriver();
        driver.goAvailable();
        when(driverRepository.findById(driver.getDriverId())).thenReturn(Optional.of(driver));
        when(vehicleRepository.findByDriverIdAndActiveTrue(driver.getDriverId()))
                .thenReturn(Optional.empty());

        DriverProfileResponse response = driverService.changeVerification(
                driver.getDriverId(), new VerificationRequest(VerificationStatus.REJECTED));

        // Waiting until their next availability change would leave a rejected driver
        // taking rides in the meantime.
        assertThat(response.availability()).isEqualTo(Availability.OFFLINE);
    }

    @Test
    @DisplayName("verification cannot be set back to PENDING")
    void changeVerification_toPending_isRejected() {
        assertThatThrownBy(() -> driverService.changeVerification(
                UUID.randomUUID(), new VerificationRequest(VerificationStatus.PENDING)))
                .isInstanceOf(BusinessRuleException.class);

        verify(driverRepository, never()).save(any());
    }

    @Test
    @DisplayName("verifying an unknown driver gives 404")
    void changeVerification_unknownDriver_throwsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(driverRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> driverService.changeVerification(
                unknown, new VerificationRequest(VerificationStatus.VERIFIED)))
                .isInstanceOf(NotFoundException.class);
    }

    // --- Public summary -----------------------------------------------------

    @Test
    @DisplayName("the public summary carries the vehicle but not the licence number")
    void getSummary_returnsVehicleWithoutLicenceDetails() {
        DriverProfile driver = readyDriver();
        when(driverRepository.findById(driver.getDriverId())).thenReturn(Optional.of(driver));
        when(vehicleRepository.findByDriverIdAndActiveTrue(driver.getDriverId()))
                .thenReturn(Optional.of(activeVehicle(driver.getDriverId())));

        DriverSummaryResponse summary = driverService.getSummary(driver.getDriverId());

        assertThat(summary.fullName()).isEqualTo("Kamal Silva");
        assertThat(summary.activeVehicle().registrationNumber()).isEqualTo("WP CAB-1234");
        // A passenger has no reason to see a driver's licence number; the record has no
        // field for it at all, which is asserted here so the guarantee is explicit.
        assertThat(DriverSummaryResponse.class.getRecordComponents())
                .noneMatch(component -> component.getName().toLowerCase().contains("licence"));
    }

    // --- Helpers ------------------------------------------------------------

    private static DriverProfile readyDriver() {
        DriverProfile driver = DriverProfile.shellFor(UUID.randomUUID(), "Kamal Silva", "+94771234567");
        driver.setVerificationStatus(VerificationStatus.VERIFIED);
        driver.updateDetails("B1234567", LocalDate.now().plusYears(2), ServiceArea.NEGOMBO);
        driver.updateLocation(7.2083, 79.8358);
        return driver;
    }

    private static Vehicle activeVehicle(UUID driverId) {
        Vehicle vehicle = Vehicle.register(driverId, "WP CAB-1234", VehicleType.CAR,
                "Toyota", "Axio", "Silver", 4, 2018);
        vehicle.activate();
        return vehicle;
    }
}
