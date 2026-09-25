package lk.ridelink.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One try at paying, successful or not.
 *
 * <p>Kept separately from {@link Payment} because a payment holds only its current state,
 * which would otherwise erase the history: a passenger whose card was declined twice
 * before succeeding would look, in the payment row alone, exactly like one who paid first
 * time. The attempt log is what makes a disputed charge explainable.</p>
 */
@Entity
@Table(name = "payment_attempt")
public class PaymentAttempt {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "payment_id", length = 36, nullable = false)
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 10)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 20)
    private PaymentOutcome outcome;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 30)
    private FailureReason failureReason;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    protected PaymentAttempt() {
        // Required by JPA.
    }

    private PaymentAttempt(UUID paymentId, PaymentMethod method, PaymentOutcome outcome,
                           FailureReason failureReason) {
        this.id = UUID.randomUUID();
        this.paymentId = paymentId;
        this.method = method;
        this.outcome = outcome;
        this.failureReason = failureReason;
        this.attemptedAt = Instant.now();
    }

    public static PaymentAttempt succeeded(UUID paymentId, PaymentMethod method) {
        return new PaymentAttempt(paymentId, method, PaymentOutcome.SUCCEEDED, null);
    }

    public static PaymentAttempt failed(UUID paymentId, PaymentMethod method, FailureReason reason) {
        return new PaymentAttempt(paymentId, method, PaymentOutcome.FAILED, reason);
    }

    public UUID getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public PaymentOutcome getOutcome() {
        return outcome;
    }

    public FailureReason getFailureReason() {
        return failureReason;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
    }
}
