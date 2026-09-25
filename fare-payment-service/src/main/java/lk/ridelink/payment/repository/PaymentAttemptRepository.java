package lk.ridelink.payment.repository;

import java.util.List;
import java.util.UUID;
import lk.ridelink.payment.domain.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {

    List<PaymentAttempt> findByPaymentIdOrderByAttemptedAtAsc(UUID paymentId);
}
