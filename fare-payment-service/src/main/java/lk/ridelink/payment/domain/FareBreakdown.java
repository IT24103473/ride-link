package lk.ridelink.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;

/**
 * The itemised parts of a fare, and the total.
 *
 * <p>Stored rather than recalculated on read, so a fare quoted to a passenger can always
 * be explained exactly as it was at the time - even after the tariffs in configuration
 * have changed.</p>
 *
 * <p>Every amount is a {@link BigDecimal} at scale 2. Money must never be a
 * {@code double}: binary floating point cannot represent 0.10 exactly, and the error
 * compounds across a base fare plus two multiplications.</p>
 *
 * @param minimumFareApplied true when the computed fare fell below the minimum and the
 *                           minimum was charged instead; recorded so the passenger can be
 *                           shown why a very short trip cost more than the maths suggests
 */
@Embeddable
public record FareBreakdown(

        @Column(name = "distance_km", nullable = false, precision = 8, scale = 2)
        BigDecimal distanceKm,

        @Column(name = "duration_min", nullable = false)
        Integer durationMin,

        @Column(name = "base_fare", nullable = false, precision = 10, scale = 2)
        BigDecimal baseFare,

        @Column(name = "distance_fare", nullable = false, precision = 10, scale = 2)
        BigDecimal distanceFare,

        @Column(name = "time_fare", nullable = false, precision = 10, scale = 2)
        BigDecimal timeFare,

        @Column(name = "minimum_fare", nullable = false, precision = 10, scale = 2)
        BigDecimal minimumFare,

        @Column(name = "minimum_fare_applied", nullable = false)
        boolean minimumFareApplied,

        @Column(name = "total", nullable = false, precision = 10, scale = 2)
        BigDecimal total
) {
}
