package lk.ridelink.payment.config;

import java.math.BigDecimal;
import java.util.Map;
import lk.ridelink.payment.domain.VehicleType;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed view of the {@code ridelink.*} configuration block.
 *
 * <p>The tariffs live here, in configuration, rather than as constants in Java. That is a
 * deliberate design decision, not a convenience: prices change far more often than code,
 * and a change should not require a rebuild, a redeploy or a code review of business
 * logic. It also means the whole pricing rule is visible in one readable place, which is
 * what {@code GET /fares/tariffs} publishes to clients.</p>
 */
@ConfigurationProperties(prefix = "ridelink")
public record RideLinkProperties(Security security, Fare fare, Map<VehicleType, Tariff> tariffs) {

    public record Security(String jwtSecret) {
    }

    /**
     * @param roadWindingFactor  multiplier turning straight-line distance into an
     *                           approximate road distance, standing in for a routing engine
     * @param averageSpeedKmh    assumed city speed, standing in for live traffic data
     * @param estimateValidityMinutes how long a quote stays honourable
     * @param cancellationFee    flat fee when a passenger cancels after a driver accepted
     */
    public record Fare(String currency,
                       BigDecimal roadWindingFactor,
                       BigDecimal averageSpeedKmh,
                       int estimateValidityMinutes,
                       BigDecimal cancellationFee) {
    }

    /**
     * One vehicle class's pricing.
     *
     * @param minimum floor price; a very short trip is charged this instead of the
     *                computed fare, so a one-minute hop still covers the driver's time
     */
    public record Tariff(BigDecimal base, BigDecimal perKm, BigDecimal perMin, BigDecimal minimum) {
    }

    /** Fails fast at startup rather than at the first quote if a tariff is missing. */
    public Tariff tariffFor(VehicleType vehicleType) {
        Tariff tariff = tariffs == null ? null : tariffs.get(vehicleType);
        if (tariff == null) {
            throw new IllegalStateException(
                    "No tariff configured for vehicle type " + vehicleType
                            + "; check ridelink.tariffs in application.yml");
        }
        return tariff;
    }
}
