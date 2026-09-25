package lk.ridelink.payment.domain;

import java.math.BigDecimal;

/**
 * Turns a distance and a duration into a priced fare.
 *
 * <p>An interface because pricing is the single most likely thing to change in a
 * ride-hailing system: surge pricing, night rates, airport surcharges and promotional
 * discounts are all new implementations rather than edits to the callers. The estimate
 * and final-fare paths both depend on this abstraction, so a new rule applies to both at
 * once and cannot drift between them.</p>
 */
public interface FareCalculator {

    /**
     * Prices a journey.
     *
     * @param vehicleType   selects the tariff
     * @param distanceKm    road distance, already adjusted for winding
     * @param durationMin   journey duration in whole minutes
     * @return the itemised breakdown, with the minimum fare applied where it bites
     */
    FareBreakdown calculate(VehicleType vehicleType, BigDecimal distanceKm, int durationMin);

    /**
     * Converts a straight-line distance into an estimated road distance and duration, then
     * prices it. Used for quotes, where no real journey has happened yet.
     */
    FareBreakdown estimate(VehicleType vehicleType, double straightLineKm);
}
