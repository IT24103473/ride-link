package lk.ridelink.account.repository;

import java.util.Optional;
import java.util.UUID;
import lk.ridelink.account.domain.Account;
import lk.ridelink.account.domain.AccountStatus;
import lk.ridelink.account.domain.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Admin listing with optional filters. Passing {@code null} for a parameter means
     * "do not filter on it", which keeps one query instead of four overloads.
     */
    @Query("""
            SELECT a FROM Account a
            WHERE (:role IS NULL OR a.role = :role)
              AND (:status IS NULL OR a.status = :status)
            """)
    Page<Account> search(@Param("role") Role role,
                         @Param("status") AccountStatus status,
                         Pageable pageable);
}
