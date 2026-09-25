package lk.ridelink.payment.domain;

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
 * What a ride actually cost, computed once the ride ends.
 *
 * <p>Usually differs slightly from the estimate, because it uses the real elapsed time and
 * the driver's reported distance rather than assumptions. That difference is intended
 * behaviour; the estimate was always a quote.</p>
 *
 * <p>{@code rideId} is unique, so a redelivered {@code ride.completed} cannot produce a
 * second fare for the same trip.</p>
 */
@Entity
@Table(name = "final_fare")
public class FinalFare {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ride_id", length = 36, nullable = false, unique = true)
    private UUID rideId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private FareType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 10)
    private VehicleType vehicleType;

    @Embedded
    private FareBreakdown breakdown;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected FinalFare() {
        // Required by JPA.
    }

    private FinalFare(UUID rideId, FareType type, VehicleType vehicleType,
                      FareBreakdown breakdown, String currency) {
        this.id = UUID.randomUUID();
        this.rideId = rideId;
        this.type = type;
        this.vehicleType = vehicleType;
        this.breakdown = breakdown;
        this.currency = currency;
        this.createdAt = Instant.now();
    }

    /** The fare for a completed journey. */
    public static FinalFare forTrip(UUID rideId, VehicleType vehicleType,
                                    FareBreakdown breakdown, String currency) {
        return new FinalFare(rideId, FareType.TRIP, vehicleType, breakdown, currency);
    }

    /** The flat fee charged when a passenger cancels after a driver had accepted. */
    public static FinalFare forCancellation(UUID rideId, VehicleType vehicleType,
                                            FareBreakdown breakdown, String currency) {
        return new FinalFare(rideId, FareType.CANCELLATION_FEE, vehicleType, breakdown, currency);
    }

    public UUID getId() {
        return id;
    }

    public UUID getRideId() {
        return rideId;
    }

    public FareType getType() {
        return type;
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

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FinalFare fare)) {
            return false;
        }
        return id != null && id.equals(fare.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
