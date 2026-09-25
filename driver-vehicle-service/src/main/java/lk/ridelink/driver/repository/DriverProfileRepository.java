package lk.ridelink.driver.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lk.ridelink.driver.domain.Availability;
import lk.ridelink.driver.domain.DriverProfile;
import lk.ridelink.driver.domain.ServiceArea;
import lk.ridelink.driver.domain.VehicleType;
import lk.ridelink.driver.domain.VerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DriverProfileRepository extends JpaRepository<DriverProfile, UUID> {

    /**
     * Applies conditions 1-7 of the matching rule (see docs/business-rules.md): available,
     * verified, account active, licence valid, correct area, fresh location, and an active
     * vehicle of the requested type.
     *
     * <p>Condition 8 - the radius - is deliberately <em>not</em> here. Haversine in SQL
     * would need database-specific trigonometric functions and would not run the same way
     * on H2 during tests, so distance is computed in Java over the small set this query
     * returns. Because the area filter has already narrowed the rows to one city, that
     * set stays small.</p>
     *
     * @param freshLocationCutoff drivers whose position is older than this are excluded,
     *                            since a stale position gives a meaningless distance
     */
    @Query("""
            SELECT new lk.ridelink.driver.repository.EligibleDriverRow(p, v)
            FROM DriverProfile p
            JOIN Vehicle v ON v.driverId = p.driverId AND v.active = true
            WHERE p.availability = :availability
              AND p.verificationStatus = :verificationStatus
              AND p.accountActive = true
              AND p.serviceArea = :serviceArea
              AND p.licenceExpiry >= :today
              AND p.currentLat IS NOT NULL
              AND p.currentLng IS NOT NULL
              AND p.locationUpdatedAt >= :freshLocationCutoff
              AND v.type = :vehicleType
            """)
    List<EligibleDriverRow> findEligible(@Param("availability") Availability availability,
                                         @Param("verificationStatus") VerificationStatus verificationStatus,
                                         @Param("serviceArea") ServiceArea serviceArea,
                                         @Param("vehicleType") VehicleType vehicleType,
                                         @Param("today") LocalDate today,
                                         @Param("freshLocationCutoff") Instant freshLocationCutoff);

    /**
     * Admin listing. A null parameter means "do not filter on it", which keeps this to one
     * query rather than eight overloads.
     */
    @Query("""
            SELECT p FROM DriverProfile p
            WHERE (:serviceArea IS NULL OR p.serviceArea = :serviceArea)
              AND (:availability IS NULL OR p.availability = :availability)
              AND (:verificationStatus IS NULL OR p.verificationStatus = :verificationStatus)
            """)
    Page<DriverProfile> search(@Param("serviceArea") ServiceArea serviceArea,
                               @Param("availability") Availability availability,
                               @Param("verificationStatus") VerificationStatus verificationStatus,
                               Pageable pageable);
}
