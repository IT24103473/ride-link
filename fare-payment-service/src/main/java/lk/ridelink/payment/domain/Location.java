package lk.ridelink.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * A place, as a name plus simulated coordinates.
 *
 * <p>Embedded rather than a separate table: a location has no identity of its own and is
 * never queried independently, so it belongs to the row that references it.</p>
 */
@Embeddable
public record Location(

        @Column(name = "name", nullable = false, length = 100)
        String name,

        @Column(name = "lat", nullable = false)
        Double lat,

        @Column(name = "lng", nullable = false)
        Double lng
) {
}
