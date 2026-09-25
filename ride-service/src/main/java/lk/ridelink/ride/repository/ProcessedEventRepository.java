package lk.ridelink.ride.repository;

import java.util.UUID;
import lk.ridelink.ride.domain.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
}
