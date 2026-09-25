package lk.ridelink.driver.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * A vehicle belonging to a driver.
 *
 * <p>A driver may register several, but only one is {@code active} at a time: the active
 * vehicle's {@link VehicleType} is what matching filters on. Modelling it this way lets a
 * driver switch between, say, a tuk and a car during the day without deleting and
 * re-adding records.</p>
 *
 * <p>The relationship to {@link DriverProfile} is held as a plain {@code driverId} rather
 * than a JPA association. That keeps the aggregate boundary explicit and avoids lazy
 * loading surprises when a vehicle is read during a driver search.</p>
 */
@Entity
@Table(name = "vehicle")
public class Vehicle {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "driver_id", length = 36, nullable = false)
    private UUID driverId;

    /** Unique across the platform, e.g. {@code WP CAB-1234}. */
    @Column(name = "registration_number", nullable = false, length = 20, unique = true)
    private String registrationNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private VehicleType type;

    @Column(name = "make", nullable = false, length = 40)
    private String make;

    @Column(name = "model", nullable = false, length = 40)
    private String model;

    @Column(name = "colour", nullable = false, length = 30)
    private String colour;

    @Column(name = "seats", nullable = false)
    private int seats;

    /**
     * Column is {@code manufacture_year}, not {@code year}: YEAR is a reserved word in
     * H2, so a column called "year" builds on MySQL but fails the test schema.
     */
    @Column(name = "manufacture_year", nullable = false)
    private int year;

    @Column(name = "active", nullable = false)
    private boolean active;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Vehicle() {
        // Required by JPA.
    }

    private Vehicle(UUID driverId, String registrationNumber, VehicleType type, String make,
                    String model, String colour, int seats, int year) {
        this.id = UUID.randomUUID();
        this.driverId = driverId;
        this.registrationNumber = registrationNumber;
        this.type = type;
        this.make = make;
        this.model = model;
        this.colour = colour;
        this.seats = seats;
        this.year = year;
        this.active = false;
    }

    /**
     * Registers a vehicle, inactive to begin with. Activation is a separate, explicit
     * step so that adding a second vehicle never silently changes which one the driver
     * is currently dispatched in.
     */
    public static Vehicle register(UUID driverId, String registrationNumber, VehicleType type,
                                   String make, String model, String colour, int seats, int year) {
        return new Vehicle(driverId, registrationNumber, type, make, model, colour, seats, year);
    }

    public void updateDetails(VehicleType type, String make, String model, String colour,
                              int seats, int year) {
        this.type = type;
        this.make = make;
        this.model = model;
        this.colour = colour;
        this.seats = seats;
        this.year = year;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public boolean belongsTo(UUID candidateDriverId) {
        return driverId.equals(candidateDriverId);
    }

    public UUID getId() {
        return id;
    }

    public UUID getDriverId() {
        return driverId;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public VehicleType getType() {
        return type;
    }

    public String getMake() {
        return make;
    }

    public String getModel() {
        return model;
    }

    public String getColour() {
        return colour;
    }

    public int getSeats() {
        return seats;
    }

    public int getYear() {
        return year;
    }

    public boolean isActive() {
        return active;
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
        if (!(other instanceof Vehicle vehicle)) {
            return false;
        }
        return id != null && id.equals(vehicle.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Vehicle{id=%s, reg=%s, type=%s, active=%s}"
                .formatted(id, registrationNumber, type, active);
    }
}
