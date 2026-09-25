package lk.ridelink.ride.repository;

import java.util.Optional;
import java.util.UUID;
import lk.ridelink.ride.domain.Ride;
import lk.ridelink.ride.domain.RideStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RideRepository extends JpaRepository<Ride, UUID> {

    /**
     * The passenger's ride that has not yet finished, if any.
     *
     * <p>Backs the one-active-ride rule. Expressed as "not in the terminal states" rather
     * than as a list of active ones, so adding a new intermediate status to the state
     * machine cannot silently leave a hole in this check.</p>
     */
    @Query("""
            SELECT r FROM Ride r
            WHERE r.passengerId = :passengerId
              AND r.status NOT IN (lk.ridelink.ride.domain.RideStatus.COMPLETED,
                                   lk.ridelink.ride.domain.RideStatus.CANCELLED)
            """)
    Optional<Ride> findActiveByPassengerId(@Param("passengerId") UUID passengerId);

    /** A passenger's own rides, newest first by the caller's sort. */
    @Query("""
            SELECT r FROM Ride r
            WHERE r.passengerId = :passengerId
              AND (:status IS NULL OR r.status = :status)
            """)
    Page<Ride> findByPassenger(@Param("passengerId") UUID passengerId,
                               @Param("status") RideStatus status,
                               Pageable pageable);

    /** Rides assigned to a driver. */
    @Query("""
            SELECT r FROM Ride r
            WHERE r.driverId = :driverId
              AND (:status IS NULL OR r.status = :status)
            """)
    Page<Ride> findByDriver(@Param("driverId") UUID driverId,
                            @Param("status") RideStatus status,
                            Pageable pageable);

    /** Admin view across every passenger and driver. */
    @Query("""
            SELECT r FROM Ride r
            WHERE (:status IS NULL OR r.status = :status)
            """)
    Page<Ride> findAllFiltered(@Param("status") RideStatus status, Pageable pageable);
}
