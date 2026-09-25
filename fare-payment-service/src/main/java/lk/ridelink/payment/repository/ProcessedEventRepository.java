package lk.ridelink.payment.repository;

import java.util.UUID;
import lk.ridelink.payment.domain.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
}
