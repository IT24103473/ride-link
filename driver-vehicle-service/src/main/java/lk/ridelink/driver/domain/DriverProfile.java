package lk.ridelink.driver.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * A driver's operational profile: who they are, whether they may drive, and where they
 * are right now.
 *
 * <p>The primary key is the <em>account id</em>, not a generated one. Reusing the
 * identity the Account Service issued means a driver has exactly one id across all four
 * services, so the Ride service can talk about "driver X" without any cross-service
 * lookup table.</p>
 *
 * <p>This service stores a copy of the driver's name and phone, fed by events rather than
 * by querying Account. That is deliberate duplication: driver search must not depend on
 * Account being up, and the data is not authoritative here.</p>
 */
@Entity
@Table(name = "driver_profile")
public class DriverProfile {

    /** Equals the Account Service's account id. */
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "driver_id", length = 36, nullable = false, updatable = false)
    private UUID driverId;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "licence_number", length = 30)
    private String licenceNumber;

    @Column(name = "licence_expiry")
    private LocalDate licenceExpiry;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_area", length = 20)
    private ServiceArea serviceArea;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private VerificationStatus verificationStatus;

    /** Mirror of the Account status, kept current by {@code account.status.changed}. */
    @Column(name = "account_active", nullable = false)
    private boolean accountActive;

    @Enumerated(EnumType.STRING)
    @Column(name = "availability", nullable = false, length = 20)
    private Availability availability;

    /** When the driver last became AVAILABLE; the tie-break in the ranking rule. */
    @Column(name = "available_since")
    private Instant availableSince;

    @Column(name = "current_lat")
    private Double currentLat;

    @Column(name = "current_lng")
    private Double currentLng;

    @Column(name = "location_updated_at")
    private Instant locationUpdatedAt;

    /** The ride this driver is reserved for; null unless BUSY. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "current_ride_id", length = 36)
    private UUID currentRideId;

    @Column(name = "completed_trips", nullable = false)
    private int completedTrips;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * The heart of safe reservation: two Ride requests racing for the same driver both
     * read version N, and only the first write succeeds. The loser gets an optimistic
     * lock failure, which the service turns into 409 DRIVER_NOT_AVAILABLE.
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected DriverProfile() {
        // Required by JPA.
    }

    private DriverProfile(UUID driverId, String fullName, String phone) {
        this.driverId = driverId;
        this.fullName = fullName;
        this.phone = phone;
        this.verificationStatus = VerificationStatus.PENDING;
        this.availability = Availability.OFFLINE;
        this.accountActive = true;
        this.completedTrips = 0;
    }

    /**
     * Creates the profile shell in response to {@code account.driver.registered}.
     * Starts PENDING and OFFLINE: a brand-new driver is not dispatchable until an admin
     * has verified them and they have added a vehicle.
     */
    public static DriverProfile shellFor(UUID driverId, String fullName, String phone) {
        return new DriverProfile(driverId, fullName, phone);
    }

    // --- Profile updates ----------------------------------------------------

    public void updateDetails(String licenceNumber, LocalDate licenceExpiry, ServiceArea serviceArea) {
        this.licenceNumber = licenceNumber;
        this.licenceExpiry = licenceExpiry;
        this.serviceArea = serviceArea;
    }

    public void updateContactDetails(String fullName, String phone) {
        this.fullName = fullName;
        this.phone = phone;
    }

    public void updateLocation(double lat, double lng) {
        this.currentLat = lat;
        this.currentLng = lng;
        this.locationUpdatedAt = Instant.now();
    }

    public void setVerificationStatus(VerificationStatus status) {
        this.verificationStatus = status;
    }

    // --- Availability -------------------------------------------------------

    /**
     * Puts the driver online. {@code availableSince} is stamped here because it is what
     * the ranking rule's tie-break uses: on equal distance, the driver who has waited
     * longest wins.
     */
    public void goAvailable() {
        this.availability = Availability.AVAILABLE;
        this.availableSince = Instant.now();
    }

    public void goOffline() {
        this.availability = Availability.OFFLINE;
        this.availableSince = null;
    }

    /**
     * Reserves this driver for a ride. Only legal from AVAILABLE, which - combined with
     * the version check on write - is what makes a double booking impossible.
     */
    public void reserveFor(UUID rideId) {
        this.availability = Availability.BUSY;
        this.currentRideId = rideId;
        this.availableSince = null;
    }

    /**
     * Frees the driver after a ride ends. Returns them to AVAILABLE rather than OFFLINE,
     * because a driver who just finished a trip is still working.
     */
    public void release() {
        this.availability = Availability.AVAILABLE;
        this.availableSince = Instant.now();
        this.currentRideId = null;
    }

    public void incrementCompletedTrips() {
        this.completedTrips++;
    }

    /**
     * Applies an account suspension. A suspended driver who was online is forced offline
     * immediately, so they stop appearing in searches; one who was mid-ride is left BUSY
     * so the trip in progress is not disrupted.
     */
    public void setAccountActive(boolean active) {
        this.accountActive = active;
        if (!active && availability == Availability.AVAILABLE) {
            goOffline();
        }
    }

    // --- Queries ------------------------------------------------------------

    public boolean isLicenceValid(LocalDate today) {
        // A licence expiring today is still valid today.
        return licenceExpiry != null && !licenceExpiry.isBefore(today);
    }

    public boolean hasLocation() {
        return currentLat != null && currentLng != null && locationUpdatedAt != null;
    }

    public boolean isBusy() {
        return availability == Availability.BUSY;
    }

    /** True when this driver is currently reserved for exactly this ride. */
    public boolean isReservedFor(UUID rideId) {
        return currentRideId != null && currentRideId.equals(rideId);
    }

    // --- Accessors ----------------------------------------------------------

    public UUID getDriverId() {
        return driverId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhone() {
        return phone;
    }

    public String getLicenceNumber() {
        return licenceNumber;
    }

    public LocalDate getLicenceExpiry() {
        return licenceExpiry;
    }

    public ServiceArea getServiceArea() {
        return serviceArea;
    }

    public VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public boolean isAccountActive() {
        return accountActive;
    }

    public Availability getAvailability() {
        return availability;
    }

    public Instant getAvailableSince() {
        return availableSince;
    }

    public Double getCurrentLat() {
        return currentLat;
    }

    public Double getCurrentLng() {
        return currentLng;
    }

    public Instant getLocationUpdatedAt() {
        return locationUpdatedAt;
    }

    public UUID getCurrentRideId() {
        return currentRideId;
    }

    public int getCompletedTrips() {
        return completedTrips;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DriverProfile profile)) {
            return false;
        }
        return driverId != null && driverId.equals(profile.driverId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(driverId);
    }

    @Override
    public String toString() {
        return "DriverProfile{driverId=%s, availability=%s, verification=%s, area=%s}"
                .formatted(driverId, availability, verificationStatus, serviceArea);
    }
}
