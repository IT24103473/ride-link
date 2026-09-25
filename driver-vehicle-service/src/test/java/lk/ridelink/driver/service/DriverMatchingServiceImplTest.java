package lk.ridelink.driver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lk.ridelink.driver.config.RideLinkProperties;
import lk.ridelink.driver.domain.Availability;
import lk.ridelink.driver.domain.DriverProfile;
import lk.ridelink.driver.domain.NearestThenLongestIdleStrategy;
import lk.ridelink.driver.domain.ServiceArea;
import lk.ridelink.driver.domain.Vehicle;
import lk.ridelink.driver.domain.VehicleType;
import lk.ridelink.driver.domain.VerificationStatus;
import lk.ridelink.driver.dto.DriverCandidateResponse;
import lk.ridelink.driver.exception.DriverNotAvailableException;
import lk.ridelink.driver.exception.NotFoundException;
import lk.ridelink.driver.mapper.DriverMapper;
import lk.ridelink.driver.repository.DriverProfileRepository;
import lk.ridelink.driver.repository.EligibleDriverRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * Matching, radius handling and the reservation race.
 *
 * <p>The reservation tests are the important ones: they cover the case where two
 * passengers request at the same moment, which is the only place in RideLink where two
 * writers genuinely compete for the same row.</p>
 */
@ExtendWith(MockitoExtension.class)
class DriverMatchingServiceImplTest {

    /** Negombo town centre, used as the pickup point throughout. */
    private static final double PICKUP_LAT = 7.2083;
    private static final double PICKUP_LNG = 79.8358;

    @Mock
    private DriverProfileRepository driverRepository;

    private DriverMatchingServiceImpl matchingService;

    @BeforeEach
    void setUp() {
        RideLinkProperties properties = new RideLinkProperties(
                new RideLinkProperties.Security("secret"),
                new RideLinkProperties.Matching(5, 20, 5, 30));

        matchingService = new DriverMatchingServiceImpl(driverRepository,
                new NearestThenLongestIdleStrategy(), new DriverMapper(), properties);
    }

    // --- Search -------------------------------------------------------------

    @Test
    @DisplayName("findAvailable: candidates are returned nearest-first")
    void findAvailable_multipleDrivers_ranksNearestFirst() {
        DriverProfile near = driverAt("near", PICKUP_LAT + 0.005, PICKUP_LNG, Instant.now());
        DriverProfile far = driverAt("far", PICKUP_LAT + 0.02, PICKUP_LNG, Instant.now());

        when(driverRepository.findEligible(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(row(far), row(near)));

        List<DriverCandidateResponse> candidates = matchingService.findAvailable(
                PICKUP_LAT, PICKUP_LNG, VehicleType.CAR, ServiceArea.NEGOMBO, null, null);

        assertThat(candidates).extracting(DriverCandidateResponse::fullName)
                .containsExactly("near", "far");
        assertThat(candidates.get(0).distanceKm()).isLessThan(candidates.get(1).distanceKm());
    }

    @Test
    @DisplayName("findAvailable: drivers beyond the radius are excluded")
    void findAvailable_driverOutsideRadius_isExcluded() {
        // Roughly 22 km north of the pickup, well outside the default 5 km radius.
        DriverProfile tooFar = driverAt("too-far", PICKUP_LAT + 0.2, PICKUP_LNG, Instant.now());
        DriverProfile close = driverAt("close", PICKUP_LAT + 0.005, PICKUP_LNG, Instant.now());

        when(driverRepository.findEligible(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(tooFar, close).stream().map(DriverMatchingServiceImplTest::row).toList());

        List<DriverCandidateResponse> candidates = matchingService.findAvailable(
                PICKUP_LAT, PICKUP_LNG, VehicleType.CAR, ServiceArea.NEGOMBO, null, null);

        assertThat(candidates).extracting(DriverCandidateResponse::fullName).containsExactly("close");
    }

    @Test
    @DisplayName("findAvailable: a requested radius above the cap is clamped to 20 km")
    void findAvailable_radiusAboveMaximum_isClampedToCap() {
        // About 33 km away: inside the 500 km the caller asked for, outside the 20 km cap.
        DriverProfile veryFar = driverAt("very-far", PICKUP_LAT + 0.3, PICKUP_LNG, Instant.now());

        when(driverRepository.findEligible(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(row(veryFar)));

        List<DriverCandidateResponse> candidates = matchingService.findAvailable(
                PICKUP_LAT, PICKUP_LNG, VehicleType.CAR, ServiceArea.NEGOMBO, 500, null);

        // Without the clamp, one crafted request could scan the whole country.
        assertThat(candidates).isEmpty();
    }

    @Test
    @DisplayName("findAvailable: the result honours the requested limit")
    void findAvailable_limitSupplied_truncatesResults() {
        when(driverRepository.findEligible(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(
                        row(driverAt("a", PICKUP_LAT + 0.001, PICKUP_LNG, Instant.now())),
                        row(driverAt("b", PICKUP_LAT + 0.002, PICKUP_LNG, Instant.now())),
                        row(driverAt("c", PICKUP_LAT + 0.003, PICKUP_LNG, Instant.now()))));

        List<DriverCandidateResponse> candidates = matchingService.findAvailable(
                PICKUP_LAT, PICKUP_LNG, VehicleType.CAR, ServiceArea.NEGOMBO, null, 2);

        assertThat(candidates).hasSize(2);
    }

    @Test
    @DisplayName("findAvailable: no eligible drivers returns an empty list, not an error")
    void findAvailable_noEligibleDrivers_returnsEmptyList() {
        when(driverRepository.findEligible(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        // Empty is a normal answer; it is the Ride service that turns it into a 409.
        assertThat(matchingService.findAvailable(PICKUP_LAT, PICKUP_LNG, VehicleType.VAN,
                ServiceArea.GALLE, null, null)).isEmpty();
    }

    @Test
    @DisplayName("findAvailable: the query filters on AVAILABLE, VERIFIED and a fresh location")
    void findAvailable_passesCorrectFiltersToRepository() {
        when(driverRepository.findEligible(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        matchingService.findAvailable(PICKUP_LAT, PICKUP_LNG, VehicleType.TUK,
                ServiceArea.KANDY, null, null);

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(driverRepository).findEligible(eq(Availability.AVAILABLE), eq(VerificationStatus.VERIFIED),
                eq(ServiceArea.KANDY), eq(VehicleType.TUK), any(LocalDate.class), cutoff.capture());

        // The freshness window is 30 minutes; anything older is a meaningless position.
        assertThat(cutoff.getValue()).isBetween(
                Instant.now().minus(31, ChronoUnit.MINUTES),
                Instant.now().minus(29, ChronoUnit.MINUTES));
    }

    // --- Reserve ------------------------------------------------------------

    @Test
    @DisplayName("reserve: an available driver becomes BUSY and is bound to the ride")
    void reserve_availableDriver_becomesBusy() {
        DriverProfile driver = driverAt("kamal", PICKUP_LAT, PICKUP_LNG, Instant.now());
        UUID rideId = UUID.randomUUID();
        when(driverRepository.findById(driver.getDriverId())).thenReturn(Optional.of(driver));
        when(driverRepository.saveAndFlush(driver)).thenReturn(driver);

        matchingService.reserve(driver.getDriverId(), rideId);

        assertThat(driver.getAvailability()).isEqualTo(Availability.BUSY);
        assertThat(driver.getCurrentRideId()).isEqualTo(rideId);
    }

    @Test
    @DisplayName("reserve: a driver who is already BUSY is refused with DRIVER_NOT_AVAILABLE")
    void reserve_alreadyBusyDriver_throwsConflict() {
        DriverProfile driver = driverAt("kamal", PICKUP_LAT, PICKUP_LNG, Instant.now());
        driver.reserveFor(UUID.randomUUID());
        when(driverRepository.findById(driver.getDriverId())).thenReturn(Optional.of(driver));

        assertThatThrownBy(() -> matchingService.reserve(driver.getDriverId(), UUID.randomUUID()))
                .isInstanceOf(DriverNotAvailableException.class);

        verify(driverRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("reserve: an OFFLINE driver is refused")
    void reserve_offlineDriver_throwsConflict() {
        DriverProfile driver = driverAt("kamal", PICKUP_LAT, PICKUP_LNG, Instant.now());
        driver.goOffline();
        when(driverRepository.findById(driver.getDriverId())).thenReturn(Optional.of(driver));

        assertThatThrownBy(() -> matchingService.reserve(driver.getDriverId(), UUID.randomUUID()))
                .isInstanceOf(DriverNotAvailableException.class);
    }

    @Test
    @DisplayName("reserve: losing the optimistic lock race is reported as DRIVER_NOT_AVAILABLE")
    void reserve_optimisticLockConflict_throwsDriverNotAvailable() {
        DriverProfile driver = driverAt("kamal", PICKUP_LAT, PICKUP_LNG, Instant.now());
        when(driverRepository.findById(driver.getDriverId())).thenReturn(Optional.of(driver));
        when(driverRepository.saveAndFlush(driver))
                .thenThrow(new OptimisticLockingFailureException("version mismatch"));

        // Two rides read the same version and both tried to reserve; this one lost. It is
        // deliberately mapped to 409 rather than 500, because the caller's correct
        // response is to try the next candidate, not to report a failure.
        assertThatThrownBy(() -> matchingService.reserve(driver.getDriverId(), UUID.randomUUID()))
                .isInstanceOf(DriverNotAvailableException.class);
    }

    @Test
    @DisplayName("reserve: an unknown driver gives 404")
    void reserve_unknownDriver_throwsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(driverRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> matchingService.reserve(unknown, UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    // --- Release ------------------------------------------------------------

    @Test
    @DisplayName("release: a driver on this ride returns to AVAILABLE")
    void release_driverOnThisRide_becomesAvailable() {
        DriverProfile driver = driverAt("kamal", PICKUP_LAT, PICKUP_LNG, Instant.now());
        UUID rideId = UUID.randomUUID();
        driver.reserveFor(rideId);
        when(driverRepository.findById(driver.getDriverId())).thenReturn(Optional.of(driver));

        matchingService.release(driver.getDriverId(), rideId);

        assertThat(driver.getAvailability()).isEqualTo(Availability.AVAILABLE);
        assertThat(driver.getCurrentRideId()).isNull();
        verify(driverRepository).save(driver);
    }

    @Test
    @DisplayName("release: calling it twice for the same ride is harmless")
    void release_calledTwice_isIdempotent() {
        DriverProfile driver = driverAt("kamal", PICKUP_LAT, PICKUP_LNG, Instant.now());
        UUID rideId = UUID.randomUUID();
        driver.reserveFor(rideId);
        when(driverRepository.findById(driver.getDriverId())).thenReturn(Optional.of(driver));

        matchingService.release(driver.getDriverId(), rideId);
        matchingService.release(driver.getDriverId(), rideId);

        // Required, because release is called both synchronously on reject and from an
        // at-least-once event consumer.
        assertThat(driver.getAvailability()).isEqualTo(Availability.AVAILABLE);
        verify(driverRepository).save(driver);
    }

    @Test
    @DisplayName("release: a stale release for an old ride does not free a driver on a new one")
    void release_forDifferentRide_leavesDriverReserved() {
        DriverProfile driver = driverAt("kamal", PICKUP_LAT, PICKUP_LNG, Instant.now());
        UUID currentRide = UUID.randomUUID();
        UUID oldRide = UUID.randomUUID();
        driver.reserveFor(currentRide);
        when(driverRepository.findById(driver.getDriverId())).thenReturn(Optional.of(driver));

        matchingService.release(driver.getDriverId(), oldRide);

        // This is the guard that makes redelivery genuinely safe: without the ride-id
        // check, a late duplicate would strand a passenger whose ride had just started.
        assertThat(driver.getAvailability()).isEqualTo(Availability.BUSY);
        assertThat(driver.getCurrentRideId()).isEqualTo(currentRide);
        verify(driverRepository, never()).save(any());
    }

    // --- Helpers ------------------------------------------------------------

    private static DriverProfile driverAt(String name, double lat, double lng, Instant availableSince) {
        DriverProfile driver = DriverProfile.shellFor(UUID.randomUUID(), name, "+94771234567");
        driver.setVerificationStatus(VerificationStatus.VERIFIED);
        driver.updateDetails("B1234567", LocalDate.now().plusYears(2), ServiceArea.NEGOMBO);
        driver.updateLocation(lat, lng);
        driver.goAvailable();
        return driver;
    }

    private static EligibleDriverRow row(DriverProfile driver) {
        Vehicle vehicle = Vehicle.register(driver.getDriverId(), "WP CAB-0001", VehicleType.CAR,
                "Toyota", "Axio", "Silver", 4, 2018);
        vehicle.activate();
        return new EligibleDriverRow(driver, vehicle);
    }
}
