package lk.ridelink.payment.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import lk.ridelink.payment.config.RideLinkProperties;
import org.springframework.stereotype.Component;

/**
 * The default pricing rule: a base fare plus a distance charge plus a time charge, floored
 * at a per-vehicle minimum.
 *
 * <pre>
 * fare  = base + (perKm x distanceKm) + (perMin x durationMin)
 * total = max(fare, minimumFare), rounded HALF_UP to 2 dp
 * </pre>
 *
 * <p>All tariffs are read from configuration, so this class contains the shape of the rule
 * but none of the numbers.</p>
 */
@Component
public class DistanceTimeFareCalculator implements FareCalculator {

    /** Money scale: LKR is quoted to two decimal places. */
    private static final int MONEY_SCALE = 2;

    /** Distances are reported to two decimals; more would be false precision. */
    private static final int DISTANCE_SCALE = 2;

    private static final int MINUTES_PER_HOUR = 60;

    private final RideLinkProperties properties;

    public DistanceTimeFareCalculator(RideLinkProperties properties) {
        this.properties = properties;
    }

    @Override
    public FareBreakdown calculate(VehicleType vehicleType, BigDecimal distanceKm, int durationMin) {
        RideLinkProperties.Tariff tariff = properties.tariffFor(vehicleType);

        BigDecimal roundedDistance = distanceKm.setScale(DISTANCE_SCALE, RoundingMode.HALF_UP);

        BigDecimal distanceFare = money(tariff.perKm().multiply(roundedDistance));
        BigDecimal timeFare = money(tariff.perMin().multiply(BigDecimal.valueOf(durationMin)));
        BigDecimal baseFare = money(tariff.base());

        BigDecimal computed = money(baseFare.add(distanceFare).add(timeFare));
        BigDecimal minimum = money(tariff.minimum());

        // The minimum bites on very short trips. Recording that it was applied lets the
        // receipt explain why the parts do not add up to the total.
        boolean minimumApplied = computed.compareTo(minimum) < 0;
        BigDecimal total = minimumApplied ? minimum : computed;

        return new FareBreakdown(roundedDistance, durationMin, baseFare, distanceFare,
                timeFare, minimum, minimumApplied, total);
    }

    @Override
    public FareBreakdown estimate(VehicleType vehicleType, double straightLineKm) {
        BigDecimal roadDistanceKm = roadDistance(straightLineKm);
        int durationMin = estimatedDurationMin(roadDistanceKm);

        return calculate(vehicleType, roadDistanceKm, durationMin);
    }

    /**
     * Approximates road distance from a straight line.
     *
     * <p>A straight line always understates a real route, which would systematically
     * underprice every ride. The winding factor is a documented stand-in for a routing
     * engine, which is out of scope for a simulated system.</p>
     */
    private BigDecimal roadDistance(double straightLineKm) {
        return BigDecimal.valueOf(straightLineKm)
                .multiply(properties.fare().roadWindingFactor())
                .setScale(DISTANCE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Duration at the assumed average city speed, rounded up and never below one minute.
     *
     * <p>Rounding up rather than to nearest means a journey is never billed for less time
     * than it took, and the one-minute floor stops a zero-distance quote pricing the time
     * component at nothing.</p>
     */
    private int estimatedDurationMin(BigDecimal roadDistanceKm) {
        BigDecimal minutes = roadDistanceKm
                .multiply(BigDecimal.valueOf(MINUTES_PER_HOUR))
                .divide(properties.fare().averageSpeedKmh(), 0, RoundingMode.CEILING);

        return Math.max(1, minutes.intValue());
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
