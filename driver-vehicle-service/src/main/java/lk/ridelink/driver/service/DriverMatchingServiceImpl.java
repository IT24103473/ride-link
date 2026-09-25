package lk.ridelink.driver.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lk.ridelink.driver.config.RideLinkProperties;
import lk.ridelink.driver.domain.Availability;
import lk.ridelink.driver.domain.DriverCandidate;
import lk.ridelink.driver.domain.DriverProfile;
import lk.ridelink.driver.domain.DriverRankingStrategy;
import lk.ridelink.driver.domain.Haversine;
import lk.ridelink.driver.domain.ServiceArea;
import lk.ridelink.driver.domain.VehicleType;
import lk.ridelink.driver.domain.VerificationStatus;
import lk.ridelink.driver.dto.DriverCandidateResponse;
import lk.ridelink.driver.exception.DriverNotAvailableException;
import lk.ridelink.driver.exception.NotFoundException;
import lk.ridelink.driver.mapper.DriverMapper;
import lk.ridelink.driver.repository.DriverProfileRepository;
import lk.ridelink.driver.repository.EligibleDriverRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Driver matching and reservation - the internal API the Ride service drives.
 *
 * <p>The full rule is documented in {@code docs/business-rules.md}; this class implements
 * it in three steps: the repository applies the eligibility filter, this class applies
 * the radius, and an injected {@link DriverRankingStrategy} decides the order.</p>
 */
@Service
public class DriverMatchingServiceImpl implements DriverMatchingService {

    private static final Logger log = LoggerFactory.getLogger(DriverMatchingServiceImpl.class);

    private final DriverProfileRepository driverRepository;
    private final DriverRankingStrategy rankingStrategy;
    private final DriverMapper driverMapper;
    private final RideLinkProperties.Matching matching;

    public DriverMatchingServiceImpl(DriverProfileRepository driverRepository,
                                     DriverRankingStrategy rankingStrategy,
                                     DriverMapper driverMapper,
                                     RideLinkProperties properties) {
        this.driverRepository = driverRepository;
        // Injected as the interface: swapping the dispatch policy is a new bean, not an
        // edit to this class.
        this.rankingStrategy = rankingStrategy;
        this.driverMapper = driverMapper;
        this.matching = properties.matching();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DriverCandidateResponse> findAvailable(double lat, double lng, VehicleType vehicleType,
                                                       ServiceArea serviceArea, Integer radiusKm,
                                                       Integer limit) {
        int radius = resolveRadius(radiusKm);
        int maxResults = limit == null || limit < 1 ? matching.defaultLimit() : limit;

        // A position older than this is not worth measuring against: the driver has
        // almost certainly moved.
        Instant freshCutoff = Instant.now().minus(matching.locationFreshnessMinutes(), ChronoUnit.MINUTES);

        List<EligibleDriverRow> eligible = driverRepository.findEligible(
                Availability.AVAILABLE, VerificationStatus.VERIFIED, serviceArea,
                vehicleType, LocalDate.now(), freshCutoff);

        // Distance is applied here rather than in SQL: Haversine needs trigonometric
        // functions that differ between MySQL and the H2 used in tests. The area filter
        // has already reduced this to the drivers in one city, so the list is small.
        List<DriverCandidate> withinRadius = eligible.stream()
                .map(row -> toCandidate(row, lat, lng))
                .filter(candidate -> candidate.distanceKm() <= radius)
                .toList();

        List<DriverCandidateResponse> ranked = rankingStrategy.rank(withinRadius).stream()
                .limit(maxResults)
                .map(driverMapper::toCandidate)
                .toList();

        log.info("Driver search in {} for {}: {} eligible, {} within {}km, returning {}",
                serviceArea, vehicleType, eligible.size(), withinRadius.size(), radius, ranked.size());

        return ranked;
    }

    @Override
    @Transactional
    public void reserve(UUID driverId, UUID rideId) {
        DriverProfile driver = require(driverId);

        // Re-checked here even though the search already filtered on it: between the
        // search and this call another ride may have taken the driver.
        if (driver.getAvailability() != Availability.AVAILABLE) {
            log.info("Reservation of driver {} for ride {} refused: availability is {}",
                    driverId, rideId, driver.getAvailability());
            throw new DriverNotAvailableException(driverId);
        }

        driver.reserveFor(rideId);

        try {
            // saveAndFlush, not save: the version check must happen now so a conflict
            // surfaces as a 409 here rather than at transaction commit, where the Ride
            // service could no longer tell it apart from a genuine failure.
            driverRepository.saveAndFlush(driver);
        } catch (OptimisticLockingFailureException ex) {
            // Two rides raced for this driver and this one lost. Expected under load,
            // not a fault: the Ride service moves on to the next candidate.
            log.info("Reservation of driver {} for ride {} lost the optimistic lock race",
                    driverId, rideId);
            throw new DriverNotAvailableException(driverId);
        }

        log.info("Driver {} reserved for ride {}", driverId, rideId);
    }

    @Override
    @Transactional
    public void release(UUID driverId, UUID rideId) {
        DriverProfile driver = require(driverId);

        // Idempotency, and protection against a stale release: only free the driver if
        // they are reserved for *this* ride. A redelivered ride.completed for an old ride
        // must not free a driver who has since started a new one.
        if (!driver.isReservedFor(rideId)) {
            log.debug("Release of driver {} for ride {} ignored: currently on ride {}",
                    driverId, rideId, driver.getCurrentRideId());
            return;
        }

        driver.release();
        driverRepository.save(driver);
        log.info("Driver {} released from ride {}", driverId, rideId);
    }

    /** Clamps the requested radius so one caller cannot scan the entire country. */
    private int resolveRadius(Integer requested) {
        if (requested == null || requested < 1) {
            return matching.defaultRadiusKm();
        }
        return Math.min(requested, matching.maxRadiusKm());
    }

    private DriverCandidate toCandidate(EligibleDriverRow row, double pickupLat, double pickupLng) {
        DriverProfile driver = row.driver();
        double distanceKm = Haversine.distanceKm(
                driver.getCurrentLat(), driver.getCurrentLng(), pickupLat, pickupLng);

        return new DriverCandidate(
                driver.getDriverId(),
                driver.getFullName(),
                distanceKm,
                driver.getAvailableSince(),
                row.vehicle().getType(),
                row.vehicle().getRegistrationNumber(),
                driver.getCompletedTrips());
    }

    private DriverProfile require(UUID driverId) {
        return driverRepository.findById(driverId)
                .orElseThrow(() -> NotFoundException.driver(driverId));
    }
}
