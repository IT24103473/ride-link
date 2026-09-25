package lk.ridelink.driver.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lk.ridelink.driver.domain.Availability;
import lk.ridelink.driver.domain.DriverProfile;
import lk.ridelink.driver.domain.ServiceArea;
import lk.ridelink.driver.domain.Vehicle;
import lk.ridelink.driver.domain.VerificationStatus;
import lk.ridelink.driver.dto.AvailabilityRequest;
import lk.ridelink.driver.dto.DriverProfileResponse;
import lk.ridelink.driver.dto.DriverSummaryResponse;
import lk.ridelink.driver.dto.LocationRequest;
import lk.ridelink.driver.dto.UpdateDriverRequest;
import lk.ridelink.driver.dto.VerificationRequest;
import lk.ridelink.driver.exception.BusinessRuleException;
import lk.ridelink.driver.exception.DriverBusyException;
import lk.ridelink.driver.exception.DriverNotEligibleException;
import lk.ridelink.driver.exception.NotFoundException;
import lk.ridelink.driver.mapper.DriverMapper;
import lk.ridelink.driver.repository.DriverProfileRepository;
import lk.ridelink.driver.repository.VehicleRepository;
import lk.ridelink.driver.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DriverServiceImpl implements DriverService {

    private static final Logger log = LoggerFactory.getLogger(DriverServiceImpl.class);

    private final DriverProfileRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final DriverMapper driverMapper;
    private final CurrentUser currentUser;

    public DriverServiceImpl(DriverProfileRepository driverRepository,
                             VehicleRepository vehicleRepository,
                             DriverMapper driverMapper,
                             CurrentUser currentUser) {
        this.driverRepository = driverRepository;
        this.vehicleRepository = vehicleRepository;
        this.driverMapper = driverMapper;
        this.currentUser = currentUser;
    }

    @Override
    @Transactional(readOnly = true)
    public DriverProfileResponse getOwnProfile() {
        DriverProfile driver = require(currentUser.id());
        return driverMapper.toProfile(driver, activeVehicleOf(driver).orElse(null));
    }

    @Override
    @Transactional
    public DriverProfileResponse updateOwnProfile(UpdateDriverRequest request) {
        DriverProfile driver = require(currentUser.id());
        driver.updateDetails(request.licenceNumber(), request.licenceExpiry(), request.serviceArea());
        driverRepository.save(driver);

        log.info("Driver {} updated licence and area to {}", driver.getDriverId(), request.serviceArea());
        return driverMapper.toProfile(driver, activeVehicleOf(driver).orElse(null));
    }

    @Override
    @Transactional
    public DriverProfileResponse changeOwnAvailability(AvailabilityRequest request) {
        DriverProfile driver = require(currentUser.id());

        // BUSY is entered only by a reservation from the Ride service. Allowing it here
        // would let a driver take themselves out of dispatch without being on a trip.
        if (request.availability() == Availability.BUSY) {
            throw new BusinessRuleException(
                    "BUSY cannot be set manually; it is entered only when a ride is assigned");
        }

        if (request.availability() == Availability.AVAILABLE) {
            goAvailable(driver);
        } else {
            goOffline(driver);
        }

        driverRepository.save(driver);
        return driverMapper.toProfile(driver, activeVehicleOf(driver).orElse(null));
    }

    private void goAvailable(DriverProfile driver) {
        List<String> reasons = DriverEligibility.reasonsNotEligible(
                driver, activeVehicleOf(driver), LocalDate.now());

        if (!reasons.isEmpty()) {
            log.info("Driver {} refused AVAILABLE: {}", driver.getDriverId(), reasons);
            throw new DriverNotEligibleException(reasons);
        }

        driver.goAvailable();
        log.info("Driver {} is now AVAILABLE in {}", driver.getDriverId(), driver.getServiceArea());
    }

    private void goOffline(DriverProfile driver) {
        // A driver mid-ride must finish or cancel it; the ride flow releases them.
        if (driver.isBusy()) {
            throw new DriverBusyException(driver.getCurrentRideId());
        }

        driver.goOffline();
        log.info("Driver {} is now OFFLINE", driver.getDriverId());
    }

    @Override
    @Transactional
    public DriverProfileResponse updateOwnLocation(LocationRequest request) {
        DriverProfile driver = require(currentUser.id());
        driver.updateLocation(request.lat(), request.lng());
        driverRepository.save(driver);

        // Logged at debug: location updates are frequent and would swamp the log at info.
        log.debug("Driver {} location updated", driver.getDriverId());
        return driverMapper.toProfile(driver, activeVehicleOf(driver).orElse(null));
    }

    @Override
    @Transactional(readOnly = true)
    public DriverSummaryResponse getSummary(UUID driverId) {
        DriverProfile driver = require(driverId);
        return driverMapper.toSummary(driver, activeVehicleOf(driver).orElse(null));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DriverProfileResponse> search(ServiceArea serviceArea, Availability availability,
                                              VerificationStatus verificationStatus, Pageable pageable) {
        return driverRepository.search(serviceArea, availability, verificationStatus, pageable)
                .map(driver -> driverMapper.toProfile(driver, activeVehicleOf(driver).orElse(null)));
    }

    @Override
    @Transactional
    public DriverProfileResponse changeVerification(UUID driverId, VerificationRequest request) {
        if (request.status() == VerificationStatus.PENDING) {
            // PENDING is the initial state, not a decision an admin can make.
            throw new BusinessRuleException("Verification can only be set to VERIFIED or REJECTED");
        }

        DriverProfile driver = require(driverId);
        driver.setVerificationStatus(request.status());

        // A driver who is online when their verification is revoked must stop being
        // matched immediately, not at their next availability change.
        if (request.status() == VerificationStatus.REJECTED
                && driver.getAvailability() == Availability.AVAILABLE) {
            driver.goOffline();
            log.info("Driver {} forced OFFLINE after verification was rejected", driverId);
        }

        driverRepository.save(driver);
        log.info("Admin set driver {} verification to {}", driverId, request.status());

        return driverMapper.toProfile(driver, activeVehicleOf(driver).orElse(null));
    }

    private Optional<Vehicle> activeVehicleOf(DriverProfile driver) {
        return vehicleRepository.findByDriverIdAndActiveTrue(driver.getDriverId());
    }

    private DriverProfile require(UUID driverId) {
        return driverRepository.findById(driverId)
                .orElseThrow(() -> NotFoundException.driver(driverId));
    }
}
