package lk.ridelink.payment.repository;

import java.util.UUID;
import lk.ridelink.payment.domain.FareEstimate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FareEstimateRepository extends JpaRepository<FareEstimate, UUID> {
}
