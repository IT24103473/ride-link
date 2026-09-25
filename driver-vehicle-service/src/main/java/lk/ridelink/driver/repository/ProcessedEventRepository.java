package lk.ridelink.driver.repository;

import java.util.UUID;
import lk.ridelink.driver.domain.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
}
