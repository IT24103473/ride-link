package lk.ridelink.payment.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import lk.ridelink.payment.domain.ReceiptCounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReceiptCounterRepository extends JpaRepository<ReceiptCounter, Integer> {

    /**
     * Reads the year's counter under a pessimistic write lock.
     *
     * <p>Pessimistic rather than optimistic here, unusually for this codebase: receipt
     * numbers must be gapless, so a losing writer cannot simply be told to retry with a
     * new number - it has to wait and take the next one in order.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ReceiptCounter c WHERE c.year = :year")
    Optional<ReceiptCounter> findByYearForUpdate(@Param("year") int year);
}
