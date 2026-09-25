package lk.ridelink.driver.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lk.ridelink.driver.domain.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    List<Vehicle> findByDriverId(UUID driverId);

    /** The vehicle a driver is currently dispatched in; matching filters on its type. */
    Optional<Vehicle> findByDriverIdAndActiveTrue(UUID driverId);

    boolean existsByRegistrationNumber(String registrationNumber);
}
