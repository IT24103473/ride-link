package lk.ridelink.ride.repository;

import java.util.List;
import java.util.UUID;
import lk.ridelink.ride.domain.RideStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RideStatusHistoryRepository extends JpaRepository<RideStatusHistory, UUID> {

    List<RideStatusHistory> findByRideIdOrderByChangedAtAsc(UUID rideId);
}
