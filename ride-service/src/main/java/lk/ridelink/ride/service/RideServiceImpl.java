package lk.ridelink.ride.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lk.ridelink.ride.client.DriverClient;
import lk.ridelink.ride.client.FareClient;
import lk.ridelink.ride.config.RideLinkProperties;
import lk.ridelink.ride.domain.CancelledBy;
import lk.ridelink.ride.domain.Location;
import lk.ridelink.ride.domain.Ride;
import lk.ridelink.ride.domain.RideStatus;
import lk.ridelink.ride.domain.RideStatusHistory;
import lk.ridelink.ride.dto.CancelRideRequest;
import lk.ridelink.ride.dto.CompleteRideRequest;
import lk.ridelink.ride.dto.CreateRideRequest;
import lk.ridelink.ride.dto.RideResponse;
import lk.ridelink.ride.dto.RideStatusHistoryResponse;
import lk.ridelink.ride.exception.ActiveRideExistsException;
import lk.ridelink.ride.exception.InvalidRequestException;
import lk.ridelink.ride.exception.NoDriverAvailableException;
import lk.ridelink.ride.exception.NotFoundException;
import lk.ridelink.ride.mapper.RideMapper;
import lk.ridelink.ride.messaging.EventPublisher;
import lk.ridelink.ride.messaging.EventTypes;
import lk.ridelink.ride.messaging.RideCancelledEvent;
import lk.ridelink.ride.messaging.RideCompletedEvent;
import lk.ridelink.ride.repository.RideRepository;
import lk.ridelink.ride.repository.RideStatusHistoryRepository;
import lk.ridelink.ride.security.CurrentUser;
import lk.ridelink.ride.security.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the ride lifecycle across the other three services.
 *
 * <p>Two patterns recur here and are worth reading as a pair:</p>
 * <ul>
 *   <li><strong>Synchronous</strong> calls (fare quote, driver search and reservation) are
 *       used where the passenger is waiting on the answer and it has to be consistent.</li>
 *   <li><strong>Events</strong> are published where the reaction can be a moment late and
 *       has several interested parties, so that completing a ride never fails because the
 *       Payment service happens to be restarting.</li>
 * </ul>
 *
 * <p>Events are published at the end of the transactional method. There is no transactional
 * outbox, so a crash in the window between commit and publish would lose the event; that
 * limitation is stated in the report, with the outbox pattern named as the improvement.</p>
 */
@Service
public class RideServiceImpl implements RideService {

    private static final Logger log = LoggerFactory.getLogger(RideServiceImpl.class);

    private final RideRepository rideRepository;
    private final RideStatusHistoryRepository historyRepository;
    private final FareClient fareClient;
    private final DriverClient driverClient;
    private final RideMapper mapper;
    private final EventPublisher eventPublisher;
    private final CurrentUser currentUser;
    private final int maxCandidates;

    public RideServiceImpl(RideRepository rideRepository,
                           RideStatusHistoryRepository historyRepository,
                           FareClient fareClient,
                           DriverClient driverClient,
                           RideMapper mapper,
                           EventPublisher eventPublisher,
                           CurrentUser currentUser,
                           RideLinkProperties properties) {
        this.rideRepository = rideRepository;
        this.historyRepository = historyRepository;
        this.fareClient = fareClient;
        this.driverClient = driverClient;
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
        this.currentUser = currentUser;
        this.maxCandidates = properties.assignment().maxCandidates();
    }

    // --- Requesting ---------------------------------------------------------

    @Override
    @Transactional
    public RideResponse requestRide(CreateRideRequest request) {
        UUID passengerId = currentUser.id();

        // One active ride at a time: a passenger can only be in one vehicle, and several
        // open rides would make driver reservations impossible to reason about.
        rideRepository.findActiveByPassengerId(passengerId).ifPresent(existing -> {
            throw new ActiveRideExistsException(existing.getId(), existing.getStatus());
        });

        Location pickup = mapper.toLocation(request.pickup());
        Location destination = mapper.toLocation(request.destination());

        // A zero-distance ride would travel nowhere and bill only the minimum fare.
        // 400 rather than 422: no state of the system would ever accept this request.
        if (pickup.isSamePlaceAs(destination)) {
            throw new InvalidRequestException("Pickup and destination must be different places");
        }

        Ride ride = Ride.request(passengerId, pickup, destination, request.vehicleType(),
                request.serviceArea(), request.paymentMethod());

        // I1, synchronous: the passenger needs the price in this same response, so there
        // is nothing to be gained by deferring it. A failure here fails the request, which
        // is correct - a ride with no price is not a usable booking.
        FareClient.FareEstimate estimate = fareClient.estimate(
                pickup, destination, request.vehicleType(), currentUser.bearerToken());

        // Snapshotted, not referenced: this is what fixes the quoted price for the life of
        // the trip even if tariffs change or the estimate expires.
        ride.applyFareEstimate(estimate.id(), estimate.breakdown().total(),
                estimate.breakdown().distanceKm(), estimate.breakdown().durationMin(),
                estimate.currency());

        rideRepository.save(ride);
        recordHistory(ride, null, RideStatus.REQUESTED, passengerId.toString(), "Ride requested");

        log.info("Ride {} requested by passenger {} ({} in {}), estimated {} {}",
                ride.getId(), passengerId, request.vehicleType(), request.serviceArea(),
                ride.getEstimatedFare(), ride.getCurrency());

        return mapper.toResponse(ride);
    }

    // --- Assignment ---------------------------------------------------------

    @Override
    @Transactional
    public RideResponse assignDriver(UUID rideId) {
        Ride ride = require(rideId);
        requirePassengerOrAdmin(ride);

        // I2, synchronous: the passenger is waiting to be told whether anyone is coming,
        // and a reservation must resolve consistently - exactly one of two simultaneous
        // requests can win the same driver.
        List<DriverClient.DriverCandidate> candidates = driverClient.findAvailable(
                ride.getPickup(), ride.getVehicleType(), ride.getServiceArea(), maxCandidates);

        if (candidates.isEmpty()) {
            log.info("No eligible drivers for ride {} in {}", rideId, ride.getServiceArea());
            throw new NoDriverAvailableException(
                    "No driver is currently available for a " + ride.getVehicleType()
                            + " in " + ride.getServiceArea() + ". The ride is still REQUESTED; try again shortly.");
        }

        // Try candidates in the order the Driver service ranked them. More than one is
        // attempted because a driver can be taken between the search and the reservation;
        // the number is bounded so a passenger does not wait while the whole city is tried.
        for (DriverClient.DriverCandidate candidate : candidates.stream().limit(maxCandidates).toList()) {
            if (driverClient.reserve(candidate.driverId(), rideId)) {
                ride.assignDriver(candidate.driverId());
                ride.transitionTo(RideStatus.ASSIGNED);
                rideRepository.save(ride);

                recordHistory(ride, RideStatus.REQUESTED, RideStatus.ASSIGNED,
                        currentUser.id().toString(),
                        "Assigned driver %s (%.2f km away)".formatted(
                                candidate.driverId(), candidate.distanceKm()));

                log.info("Ride {} assigned to driver {} ({} km away)",
                        rideId, candidate.driverId(), candidate.distanceKm());

                return mapper.toResponse(ride);
            }
            log.debug("Candidate {} was unavailable for ride {}; trying the next",
                    candidate.driverId(), rideId);
        }

        // Every candidate was taken. The ride deliberately stays REQUESTED rather than
        // being cancelled, so the passenger can simply retry rather than rebook.
        log.info("All {} candidates were taken for ride {}", candidates.size(), rideId);
        throw new NoDriverAvailableException(
                "All nearby drivers were taken while assigning. The ride is still REQUESTED; try again shortly.");
    }

    // --- Driver actions -----------------------------------------------------

    @Override
    @Transactional
    public RideResponse accept(UUID rideId) {
        Ride ride = require(rideId);
        requireAssignedDriver(ride);

        ride.transitionTo(RideStatus.ACCEPTED);
        rideRepository.save(ride);
        recordHistory(ride, RideStatus.ASSIGNED, RideStatus.ACCEPTED,
                currentUser.id().toString(), "Driver accepted");

        log.info("Ride {} accepted by driver {}", rideId, ride.getDriverId());
        return mapper.toResponse(ride);
    }

    @Override
    @Transactional
    public RideResponse reject(UUID rideId) {
        Ride ride = require(rideId);
        requireAssignedDriver(ride);

        UUID rejectingDriver = ride.getDriverId();

        // Back to REQUESTED, not CANCELLED: the passenger still wants a ride, so it
        // returns to the pool to be matched with someone else.
        ride.transitionTo(RideStatus.REQUESTED);
        rideRepository.save(ride);

        // Released synchronously so the driver is immediately available again. Failures
        // here are swallowed by the client, because the rejection itself has succeeded.
        driverClient.release(rejectingDriver, rideId);

        recordHistory(ride, RideStatus.ASSIGNED, RideStatus.REQUESTED,
                rejectingDriver.toString(), "Driver rejected; ride returned to the pool");

        log.info("Ride {} rejected by driver {}; back to REQUESTED", rideId, rejectingDriver);
        return mapper.toResponse(ride);
    }

    @Override
    @Transactional
    public RideResponse start(UUID rideId) {
        Ride ride = require(rideId);
        requireAssignedDriver(ride);

        ride.transitionTo(RideStatus.IN_PROGRESS);
        rideRepository.save(ride);
        recordHistory(ride, RideStatus.ACCEPTED, RideStatus.IN_PROGRESS,
                currentUser.id().toString(), "Trip started");

        log.info("Ride {} started", rideId);
        return mapper.toResponse(ride);
    }

    @Override
    @Transactional
    public RideResponse complete(UUID rideId, CompleteRideRequest request) {
        Ride ride = require(rideId);
        requireAssignedDriver(ride);

        ride.transitionTo(RideStatus.COMPLETED);
        rideRepository.save(ride);
        recordHistory(ride, RideStatus.IN_PROGRESS, RideStatus.COMPLETED,
                currentUser.id().toString(), "Trip completed");

        BigDecimal actualDistanceKm = request == null ? null : request.actualDistanceKm();

        // Asynchronous on purpose: releasing the driver and pricing the trip must not make
        // the driver's "complete" request fail or hang if either service is slow. Both
        // consumers receive this one event.
        eventPublisher.publish(EventTypes.RIDE_COMPLETED, new RideCompletedEvent(
                ride.getId(),
                ride.getPassengerId(),
                ride.getDriverId(),
                ride.getVehicleType().name(),
                mapper.toLocationPayload(ride.getPickup()),
                mapper.toLocationPayload(ride.getDestination()),
                toDouble(ride.getEstimatedDistanceKm()),
                toDouble(actualDistanceKm),
                ride.getStartedAt(),
                ride.getCompletedAt(),
                ride.getFareEstimateId(),
                ride.getPaymentMethod().name()));

        log.info("Ride {} completed (actual distance {})", rideId, actualDistanceKm);
        return mapper.toResponse(ride);
    }

    // --- Cancellation -------------------------------------------------------

    @Override
    @Transactional
    public RideResponse cancel(UUID rideId, CancelRideRequest request) {
        Ride ride = require(rideId);
        CancelledBy cancelledBy = resolveCanceller(ride);

        // Captured before the transition, because the fee rule keys on the status the ride
        // held immediately before cancellation - by the time the event is published the
        // ride is already CANCELLED.
        RideStatus previousStatus = ride.getStatus();
        UUID assignedDriver = ride.getDriverId();

        // Refuses a cancellation of an IN_PROGRESS, COMPLETED or already CANCELLED ride.
        ride.transitionTo(RideStatus.CANCELLED);
        ride.recordCancellation(cancelledBy, request.reason());
        rideRepository.save(ride);

        recordHistory(ride, previousStatus, RideStatus.CANCELLED,
                currentUser.id().toString(),
                "Cancelled by " + cancelledBy + ": " + request.reason());

        eventPublisher.publish(EventTypes.RIDE_CANCELLED, new RideCancelledEvent(
                ride.getId(),
                ride.getPassengerId(),
                assignedDriver,
                previousStatus.name(),
                cancelledBy.name(),
                ride.getCancelledAt(),
                ride.getVehicleType().name(),
                ride.getPaymentMethod().name()));

        log.info("Ride {} cancelled by {} from {}", rideId, cancelledBy, previousStatus);
        return mapper.toResponse(ride);
    }

    // --- Reads --------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public RideResponse getById(UUID rideId) {
        Ride ride = require(rideId);
        requireParticipantOrAdmin(ride);
        return mapper.toResponse(ride);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RideStatusHistoryResponse> getHistory(UUID rideId) {
        Ride ride = require(rideId);
        requireParticipantOrAdmin(ride);

        return historyRepository.findByRideIdOrderByChangedAtAsc(rideId).stream()
                .map(mapper::toHistoryResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RideResponse> list(RideStatus status, Pageable pageable) {
        Role role = currentUser.role();
        UUID callerId = currentUser.id();

        // Scoped by role rather than filtered afterwards, so a passenger's query can never
        // return another passenger's rides even by accident.
        Page<Ride> rides = switch (role) {
            case ADMIN -> rideRepository.findAllFiltered(status, pageable);
            case DRIVER -> rideRepository.findByDriver(callerId, status, pageable);
            default -> rideRepository.findByPassenger(callerId, status, pageable);
        };

        return rides.map(mapper::toResponse);
    }

    // --- Authorisation ------------------------------------------------------

    /**
     * Decides who is cancelling, and refuses anyone not involved.
     *
     * <p>Determined from the caller's relationship to the ride rather than taken from the
     * request body, so a passenger cannot claim a cancellation was the driver's and dodge
     * the fee.</p>
     */
    private CancelledBy resolveCanceller(Ride ride) {
        if (currentUser.isAdmin()) {
            return CancelledBy.ADMIN;
        }
        UUID callerId = currentUser.id();
        if (ride.isOwnedBy(callerId)) {
            return CancelledBy.PASSENGER;
        }
        if (ride.isAssignedTo(callerId)) {
            return CancelledBy.DRIVER;
        }
        throw new AccessDeniedException("Only the passenger, the assigned driver or an admin may cancel this ride");
    }

    private void requirePassengerOrAdmin(Ride ride) {
        if (!currentUser.isAdmin() && !ride.isOwnedBy(currentUser.id())) {
            throw new AccessDeniedException("Only the ride's passenger or an admin may do this");
        }
    }

    private void requireAssignedDriver(Ride ride) {
        if (!ride.isAssignedTo(currentUser.id())) {
            // Deliberately not admin-overridable: accepting or completing a ride is a
            // statement about what physically happened, which only the driver can make.
            throw new AccessDeniedException("Only the driver assigned to this ride may do this");
        }
    }

    private void requireParticipantOrAdmin(Ride ride) {
        if (currentUser.isAdmin()) {
            return;
        }
        UUID callerId = currentUser.id();
        if (!ride.isOwnedBy(callerId) && !ride.isAssignedTo(callerId)) {
            throw new AccessDeniedException("You were not involved in this ride");
        }
    }

    // --- Helpers ------------------------------------------------------------

    private void recordHistory(Ride ride, RideStatus from, RideStatus to, String changedBy, String note) {
        historyRepository.save(RideStatusHistory.of(ride.getId(), from, to, changedBy, note));
    }

    private Ride require(UUID rideId) {
        return rideRepository.findById(rideId).orElseThrow(() -> NotFoundException.ride(rideId));
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
