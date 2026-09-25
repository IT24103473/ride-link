package lk.ridelink.driver.service;

import java.util.UUID;
import lk.ridelink.driver.domain.Availability;
import lk.ridelink.driver.domain.ServiceArea;
import lk.ridelink.driver.domain.VerificationStatus;
import lk.ridelink.driver.dto.AvailabilityRequest;
import lk.ridelink.driver.dto.DriverProfileResponse;
import lk.ridelink.driver.dto.DriverSummaryResponse;
import lk.ridelink.driver.dto.LocationRequest;
import lk.ridelink.driver.dto.UpdateDriverRequest;
import lk.ridelink.driver.dto.VerificationRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Driver profile, availability and location. */
public interface DriverService {

    DriverProfileResponse getOwnProfile();

    DriverProfileResponse updateOwnProfile(UpdateDriverRequest request);

    /**
     * Sets availability to AVAILABLE or OFFLINE.
     *
     * @throws lk.ridelink.driver.exception.DriverNotEligibleException going AVAILABLE
     *         without meeting every requirement, with the reasons listed
     * @throws lk.ridelink.driver.exception.DriverBusyException going OFFLINE mid-ride
     */
    DriverProfileResponse changeOwnAvailability(AvailabilityRequest request);

    DriverProfileResponse updateOwnLocation(LocationRequest request);

    /** Public summary; excludes licence details. */
    DriverSummaryResponse getSummary(UUID driverId);

    Page<DriverProfileResponse> search(ServiceArea serviceArea, Availability availability,
                                       VerificationStatus verificationStatus, Pageable pageable);

    DriverProfileResponse changeVerification(UUID driverId, VerificationRequest request);
}
