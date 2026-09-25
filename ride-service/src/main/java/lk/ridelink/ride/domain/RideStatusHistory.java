package lk.ridelink.ride.domain;

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
 * An audit row for one status change.
 *
 * <p>The ride itself holds only its current status, which would otherwise lose the story:
 * a ride that was rejected by two drivers before the third accepted looks, from the ride
 * row alone, exactly like one accepted immediately. The history is what makes a disputed
 * trip reconstructable, and it is what {@code GET /rides/{id}/history} returns.</p>
 */
@Entity
@Table(name = "ride_status_history")
public class RideStatusHistory {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ride_id", length = 36, nullable = false)
    private UUID rideId;

    /** Null for the row recording the ride's creation. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private RideStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private RideStatus toStatus;

    /** The account id that caused the change, or "SYSTEM" for an event-driven one. */
    @Column(name = "changed_by", nullable = false, length = 40)
    private String changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "note", length = 255)
    private String note;

    protected RideStatusHistory() {
        // Required by JPA.
    }

    private RideStatusHistory(UUID rideId, RideStatus fromStatus, RideStatus toStatus,
                              String changedBy, String note) {
        this.id = UUID.randomUUID();
        this.rideId = rideId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedBy = changedBy;
        this.note = note;
        this.changedAt = Instant.now();
    }

    public static RideStatusHistory of(UUID rideId, RideStatus fromStatus, RideStatus toStatus,
                                       String changedBy, String note) {
        return new RideStatusHistory(rideId, fromStatus, toStatus, changedBy, note);
    }

    public UUID getId() {
        return id;
    }

    public UUID getRideId() {
        return rideId;
    }

    public RideStatus getFromStatus() {
        return fromStatus;
    }

    public RideStatus getToStatus() {
        return toStatus;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public String getNote() {
        return note;
    }
}
