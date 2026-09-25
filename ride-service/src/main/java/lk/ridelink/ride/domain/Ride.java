package lk.ridelink.ride.domain;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lk.ridelink.ride.exception.InvalidRideTransitionException;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A trip, from request through to completion or cancellation.
 *
 * <p>Every status change goes through {@link #transitionTo}, which consults
 * {@link RideStatus}. Because the check lives on the entity rather than in each service
 * method, an illegal move is impossible regardless of which endpoint attempts it - a new
 * endpoint inherits the rule for free.</p>
 *
 * <p>The fare fields are a <em>snapshot</em> taken at request time, not a live reference.
 * Copying the estimate onto the ride is what fixes the price a passenger was quoted, even
 * if tariffs change or the estimate later expires.</p>
 */
@Entity
@Table(name = "ride")
public class Ride {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "passenger_id", length = 36, nullable = false)
    private UUID passengerId;

    /** Null until a driver is reserved, and cleared again if they reject. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "driver_id", length = 36)
    private UUID driverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 10)
    private VehicleType vehicleType;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_area", nullable = false, length = 20)
    private ServiceArea serviceArea;

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
    @Column(name = "status", nullable = false, length = 20)
    private RideStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 10)
    private PaymentMethod paymentMethod;

    // --- Fare snapshot, taken from the estimate at request time ---

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "fare_estimate_id", length = 36)
    private UUID fareEstimateId;

    @Column(name = "estimated_fare", precision = 10, scale = 2)
    private BigDecimal estimatedFare;

    @Column(name = "estimated_distance_km", precision = 8, scale = 2)
    private BigDecimal estimatedDistanceKm;

    @Column(name = "estimated_duration_min")
    private Integer estimatedDurationMin;

    @Column(name = "currency", length = 3)
    private String currency;

    // --- Lifecycle timestamps ---

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancelled_by", length = 20)
    private CancelledBy cancelledBy;

    @Column(name = "cancellation_reason", length = 255)
    private String cancellationReason;

    /** Read model fed by payment events; this service does not own it. */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus;

    /** Guards against two concurrent actions on the same ride, e.g. accept and cancel. */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Ride() {
        // Required by JPA.
    }

    private Ride(UUID passengerId, Location pickup, Location destination, VehicleType vehicleType,
                 ServiceArea serviceArea, PaymentMethod paymentMethod) {
        this.id = UUID.randomUUID();
        this.passengerId = passengerId;
        this.pickup = pickup;
        this.destination = destination;
        this.vehicleType = vehicleType;
        this.serviceArea = serviceArea;
        this.paymentMethod = paymentMethod;
        this.status = RideStatus.REQUESTED;
        this.paymentStatus = PaymentStatus.NOT_DUE;
        this.requestedAt = Instant.now();
    }

    public static Ride request(UUID passengerId, Location pickup, Location destination,
                               VehicleType vehicleType, ServiceArea serviceArea,
                               PaymentMethod paymentMethod) {
        return new Ride(passengerId, pickup, destination, vehicleType, serviceArea, paymentMethod);
    }

    /** Copies the quoted price onto the ride, fixing it for the life of the trip. */
    public void applyFareEstimate(UUID fareEstimateId, BigDecimal estimatedFare,
                                  BigDecimal estimatedDistanceKm, Integer estimatedDurationMin,
                                  String currency) {
        this.fareEstimateId = fareEstimateId;
        this.estimatedFare = estimatedFare;
        this.estimatedDistanceKm = estimatedDistanceKm;
        this.estimatedDurationMin = estimatedDurationMin;
        this.currency = currency;
    }

    // --- The state machine --------------------------------------------------

    /**
     * Moves the ride to {@code target}, refusing anything the state machine forbids.
     *
     * <p>The only way the status field is ever written. Timestamps for the new state are
     * stamped here too, so a status can never be set without its corresponding time.</p>
     *
     * @throws InvalidRideTransitionException if the move is not allowed from the current
     *                                        status - surfaced as 409, because it is a
     *                                        conflict with the ride's state rather than a
     *                                        malformed request
     */
    public void transitionTo(RideStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidRideTransitionException(id, status, target);
        }

        RideStatus previous = status;
        this.status = target;

        Instant now = Instant.now();
        switch (target) {
            case ASSIGNED -> this.assignedAt = now;
            case ACCEPTED -> this.acceptedAt = now;
            case IN_PROGRESS -> this.startedAt = now;
            case COMPLETED -> {
                this.completedAt = now;
                // A completed ride now owes money; the read model reflects that
                // immediately, before the payment service has even seen the event.
                this.paymentStatus = PaymentStatus.PENDING;
            }
            case CANCELLED -> this.cancelledAt = now;
            case REQUESTED -> {
                // Returning to the pool after a rejection: clear the assignment so the
                // ride looks exactly as it did before, and can be matched afresh.
                this.assignedAt = null;
                this.driverId = null;
            }
        }

        // Kept deliberately: the previous status is what the cancellation-fee rule keys on.
        this.lastTransitionFrom = previous;
    }

    /** Not persisted; only used to report the previous status on the cancellation event. */
    @jakarta.persistence.Transient
    private RideStatus lastTransitionFrom;

    public RideStatus getLastTransitionFrom() {
        return lastTransitionFrom;
    }

    public void assignDriver(UUID driverId) {
        this.driverId = driverId;
    }

    public void recordCancellation(CancelledBy by, String reason) {
        this.cancelledBy = by;
        this.cancellationReason = reason;
    }

    /** Applied from payment events; the Fare &amp; Payment service owns the real value. */
    public void updatePaymentStatus(PaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    // --- Queries ------------------------------------------------------------

    public boolean isOwnedBy(UUID candidatePassengerId) {
        return passengerId.equals(candidatePassengerId);
    }

    public boolean isAssignedTo(UUID candidateDriverId) {
        return driverId != null && driverId.equals(candidateDriverId);
    }

    /** True while the ride still ties up the passenger. */
    public boolean isActive() {
        return status.isActive();
    }

    // --- Accessors ----------------------------------------------------------

    public UUID getId() {
        return id;
    }

    public UUID getPassengerId() {
        return passengerId;
    }

    public UUID getDriverId() {
        return driverId;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    public ServiceArea getServiceArea() {
        return serviceArea;
    }

    public Location getPickup() {
        return pickup;
    }

    public Location getDestination() {
        return destination;
    }

    public RideStatus getStatus() {
        return status;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public UUID getFareEstimateId() {
        return fareEstimateId;
    }

    public BigDecimal getEstimatedFare() {
        return estimatedFare;
    }

    public BigDecimal getEstimatedDistanceKm() {
        return estimatedDistanceKm;
    }

    public Integer getEstimatedDurationMin() {
        return estimatedDurationMin;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public CancelledBy getCancelledBy() {
        return cancelledBy;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Ride ride)) {
            return false;
        }
        return id != null && id.equals(ride.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Ride{id=%s, status=%s, passengerId=%s, driverId=%s}"
                .formatted(id, status, passengerId, driverId);
    }
}
