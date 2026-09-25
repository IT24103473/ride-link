package lk.ridelink.payment.repository;

import java.util.Optional;
import java.util.UUID;
import lk.ridelink.payment.domain.FareType;
import lk.ridelink.payment.domain.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByRideId(UUID rideId);

    /** Backs the "already charged?" check that pairs with the (ride_id, type) unique index. */
    boolean existsByRideIdAndType(UUID rideId, FareType type);

    Page<Payment> findByPassengerId(UUID passengerId, Pageable pageable);
}
