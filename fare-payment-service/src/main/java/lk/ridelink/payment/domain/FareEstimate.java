package lk.ridelink.payment.domain;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A price quoted to a passenger before they book.
 *
 * <p>Estimates expire. Without an expiry a passenger could hold a quote indefinitely and
 * present it after tariffs rose; with one, a stale quote must be refreshed. The Ride
 * service snapshots the estimate onto the ride at booking time, so a later tariff change
 * cannot alter a price already agreed.</p>
 */
@Entity
@Table(name = "fare_estimate")
public class FareEstimate {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "passenger_id", length = 36, nullable = false)
    private UUID passengerId;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "name", column = @Column(name = "pickup_name", nullable = false, length = 100)),
            @AttributeOverride(name = "lat", column = @Column(name = "pickup_lat", nullable = false)),
            @AttributeOverride(name = "lng", column = @Column(name = "pickup_lng", nullable = false))
    })
    private Location pickup;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "name", column = @Column(name = "destination_name", nullable = false, length = 100)),
            @AttributeOverride(name = "lat", column = @Column(name = "destination_lat", nullable = false)),
            @AttributeOverride(name = "lng", column = @Column(name = "destination_lng", nullable = false))
    })
    private Location destination;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 10)
    private VehicleType vehicleType;

    @Embedded
    private FareBreakdown breakdown;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected FareEstimate() {
        // Required by JPA.
    }

    private FareEstimate(UUID passengerId, Location pickup, Location destination,
                         VehicleType vehicleType, FareBreakdown breakdown, String currency,
                         int validityMinutes) {
        this.id = UUID.randomUUID();
        this.passengerId = passengerId;
        this.pickup = pickup;
        this.destination = destination;
        this.vehicleType = vehicleType;
        this.breakdown = breakdown;
        this.currency = currency;
        this.createdAt = Instant.now();
        this.expiresAt = this.createdAt.plusSeconds(validityMinutes * 60L);
    }

    public static FareEstimate create(UUID passengerId, Location pickup, Location destination,
                                      VehicleType vehicleType, FareBreakdown breakdown,
                                      String currency, int validityMinutes) {
        return new FareEstimate(passengerId, pickup, destination, vehicleType, breakdown,
                currency, validityMinutes);
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    public boolean belongsTo(UUID candidatePassengerId) {
        return passengerId.equals(candidatePassengerId);
    }

    public UUID getId() {
        return id;
    }

    public UUID getPassengerId() {
        return passengerId;
    }

    public Location getPickup() {
        return pickup;
    }

    public Location getDestination() {
        return destination;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    public FareBreakdown getBreakdown() {
        return breakdown;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FareEstimate estimate)) {
            return false;
        }
        return id != null && id.equals(estimate.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
