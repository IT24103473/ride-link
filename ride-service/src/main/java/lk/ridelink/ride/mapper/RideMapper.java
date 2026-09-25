package lk.ridelink.ride.mapper;

import lk.ridelink.ride.domain.Location;
import lk.ridelink.ride.domain.Ride;
import lk.ridelink.ride.domain.RideStatusHistory;
import lk.ridelink.ride.dto.LocationRequest;
import lk.ridelink.ride.dto.LocationResponse;
import lk.ridelink.ride.dto.RideResponse;
import lk.ridelink.ride.dto.RideStatusHistoryResponse;
import lk.ridelink.ride.messaging.LocationPayload;
import org.springframework.stereotype.Component;

/** Entity to DTO conversion, and entity to event payload. */
@Component
public class RideMapper {

    public Location toLocation(LocationRequest request) {
        return new Location(request.name(), request.lat(), request.lng());
    }

    public LocationResponse toLocationResponse(Location location) {
        return new LocationResponse(location.name(), location.lat(), location.lng());
    }

    /** Separate from the response type: events are a contract with other services. */
    public LocationPayload toLocationPayload(Location location) {
        return new LocationPayload(location.name(), location.lat(), location.lng());
    }

    public RideResponse toResponse(Ride ride) {
        return new RideResponse(
                ride.getId(),
                ride.getPassengerId(),
                ride.getDriverId(),
                ride.getVehicleType(),
                ride.getServiceArea(),
                toLocationResponse(ride.getPickup()),
                toLocationResponse(ride.getDestination()),
                ride.getStatus(),
                ride.getPaymentMethod(),
                ride.getFareEstimateId(),
                ride.getEstimatedFare(),
                ride.getEstimatedDistanceKm(),
                ride.getEstimatedDurationMin(),
                ride.getCurrency(),
                ride.getRequestedAt(),
                ride.getAssignedAt(),
                ride.getAcceptedAt(),
                ride.getStartedAt(),
                ride.getCompletedAt(),
                ride.getCancelledAt(),
                ride.getCancelledBy(),
                ride.getCancellationReason(),
                ride.getPaymentStatus());
    }

    public RideStatusHistoryResponse toHistoryResponse(RideStatusHistory history) {
        return new RideStatusHistoryResponse(
                history.getId(),
                history.getFromStatus(),
                history.getToStatus(),
                history.getChangedBy(),
                history.getChangedAt(),
                history.getNote());
    }
}
