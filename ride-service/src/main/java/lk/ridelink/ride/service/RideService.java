package lk.ridelink.ride.service;

import java.util.List;
import java.util.UUID;
import lk.ridelink.ride.domain.RideStatus;
import lk.ridelink.ride.dto.CancelRideRequest;
import lk.ridelink.ride.dto.CompleteRideRequest;
import lk.ridelink.ride.dto.CreateRideRequest;
import lk.ridelink.ride.dto.RideResponse;
import lk.ridelink.ride.dto.RideStatusHistoryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * The ride lifecycle.
 *
 * <p>Every method that changes a status funnels through the entity's state machine, so an
 * illegal move is refused no matter which endpoint was called.</p>
 */
public interface RideService {

    /**
     * Requests a ride, calling the Fare service for a quote and snapshotting it.
     *
     * @throws lk.ridelink.ride.exception.ActiveRideExistsException the passenger already
     *         has an unfinished ride
     */
    RideResponse requestRide(CreateRideRequest request);

    /**
     * Finds and reserves a driver.
     *
     * @throws lk.ridelink.ride.exception.NoDriverAvailableException nobody could be
     *         reserved; the ride stays REQUESTED so it can be retried
     */
    RideResponse assignDriver(UUID rideId);

    RideResponse accept(UUID rideId);

    /** Returns the ride to the pool and frees the driver. */
    RideResponse reject(UUID rideId);

    RideResponse start(UUID rideId);

    /** Publishes {@code ride.completed}, which drives the final fare and driver release. */
    RideResponse complete(UUID rideId, CompleteRideRequest request);

    /** Publishes {@code ride.cancelled} carrying the status held before cancellation. */
    RideResponse cancel(UUID rideId, CancelRideRequest request);

    RideResponse getById(UUID rideId);

    List<RideStatusHistoryResponse> getHistory(UUID rideId);

    /** Scoped by role: a passenger sees their own, a driver theirs, an admin all. */
    Page<RideResponse> list(RideStatus status, Pageable pageable);
}
