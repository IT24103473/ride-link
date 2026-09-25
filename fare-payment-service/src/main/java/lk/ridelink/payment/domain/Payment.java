package lk.ridelink.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * A charge raised against a ride.
 *
 * <p>The unique constraint on {@code (ride_id, type)} is the important part of this class.
 * It is what makes double charging impossible at the database level rather than merely
 * unlikely: however many times {@code ride.completed} is redelivered, a ride can carry at
 * most one TRIP payment and at most one CANCELLATION_FEE.</p>
 *
 * <p><strong>No card data is ever stored.</strong> The tokens this service accepts are
 * fixed placeholders that select a simulated outcome; only that outcome is persisted.</p>
 */
@Entity
@Table(name = "payment",
        uniqueConstraints = @UniqueConstraint(name = "uk_payment_ride_type",
                columnNames = {"ride_id", "type"}))
public class Payment {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ride_id", length = 36, nullable = false)
    private UUID rideId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "passenger_id", length = 36, nullable = false)
    private UUID passengerId;

    /** Null for a cancellation fee raised before any driver was assigned. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "driver_id", length = 36)
    private UUID driverId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "final_fare_id", length = 36, nullable = false)
    private UUID finalFareId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private FareType type;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /** The method chosen on the ride; a passenger may still pay by another means. */
    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 10)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    /** Allocated only on success, so an unpaid ride can never show a receipt number. */
    @Column(name = "receipt_number", length = 20, unique = true)
    private String receiptNumber;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 30)
    private FailureReason failureReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Stops two concurrent pay requests both marking the same payment succeeded. */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Payment() {
        // Required by JPA.
    }

    private Payment(UUID rideId, UUID passengerId, UUID driverId, UUID finalFareId,
                    FareType type, BigDecimal amount, String currency, PaymentMethod method) {
        this.id = UUID.randomUUID();
        this.rideId = rideId;
        this.passengerId = passengerId;
        this.driverId = driverId;
        this.finalFareId = finalFareId;
        this.type = type;
        this.amount = amount;
        this.currency = currency;
        this.method = method;
        this.status = PaymentStatus.PENDING;
    }

    public static Payment pending(UUID rideId, UUID passengerId, UUID driverId, UUID finalFareId,
                                  FareType type, BigDecimal amount, String currency,
                                  PaymentMethod method) {
        return new Payment(rideId, passengerId, driverId, finalFareId, type, amount, currency, method);
    }

    /**
     * Marks the payment paid and attaches its receipt number.
     *
     * <p>The receipt number is allocated here, at the moment of success, which is why an
     * unpaid payment can never have one.</p>
     */
    public void markSucceeded(String receiptNumber, PaymentMethod usedMethod) {
        this.status = PaymentStatus.SUCCEEDED;
        this.method = usedMethod;
        this.receiptNumber = receiptNumber;
        this.paidAt = Instant.now();
        // Cleared so a previously failed, then retried, payment does not keep a stale reason.
        this.failureReason = null;
    }

    /** Records a decline. FAILED is not terminal: the passenger may try again. */
    public void markFailed(FailureReason reason, PaymentMethod attemptedMethod) {
        this.status = PaymentStatus.FAILED;
        this.method = attemptedMethod;
        this.failureReason = reason;
    }

    public boolean isPaid() {
        return status == PaymentStatus.SUCCEEDED;
    }

    public boolean belongsTo(UUID candidateId) {
        return passengerId.equals(candidateId)
                || (driverId != null && driverId.equals(candidateId));
    }

    public UUID getId() {
        return id;
    }

    public UUID getRideId() {
        return rideId;
    }

    public UUID getPassengerId() {
        return passengerId;
    }

    public UUID getDriverId() {
        return driverId;
    }

    public UUID getFinalFareId() {
        return finalFareId;
    }

    public FareType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getReceiptNumber() {
        return receiptNumber;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public FailureReason getFailureReason() {
        return failureReason;
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
        if (!(other instanceof Payment payment)) {
            return false;
        }
        return id != null && id.equals(payment.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Payment{id=%s, rideId=%s, type=%s, status=%s, amount=%s}"
                .formatted(id, rideId, type, status, amount);
    }
}
