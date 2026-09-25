package lk.ridelink.payment.repository;

import java.util.Optional;
import java.util.UUID;
import lk.ridelink.payment.domain.FinalFare;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinalFareRepository extends JpaRepository<FinalFare, UUID> {

    Optional<FinalFare> findByRideId(UUID rideId);

    boolean existsByRideId(UUID rideId);
}
