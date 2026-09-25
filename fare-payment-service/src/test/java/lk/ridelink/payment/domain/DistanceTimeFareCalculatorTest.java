package lk.ridelink.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Map;
import lk.ridelink.payment.config.RideLinkProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The pricing rule, including every boundary that changes the answer.
 *
 * <p>Fares are the one part of this system where a wrong number is both invisible and
 * consequential: nothing errors, the passenger is simply charged incorrectly. So the
 * minimum-fare threshold is tested from both sides, rounding is asserted exactly, and each
 * vehicle class is checked against its own tariff.</p>
 */
class DistanceTimeFareCalculatorTest {

    private DistanceTimeFareCalculator calculator;

    @BeforeEach
    void setUp() {
        RideLinkProperties properties = new RideLinkProperties(
                new RideLinkProperties.Security("secret"),
                new RideLinkProperties.Fare("LKR",
                        new BigDecimal("1.3"),    // road winding factor
                        new BigDecimal("25"),     // average km/h
                        15,
                        new BigDecimal("100.00")),
                Map.of(
                        VehicleType.TUK, tariff("100.00", "80.00", "3.00", "200.00"),
                        VehicleType.CAR, tariff("150.00", "100.00", "5.00", "300.00"),
                        VehicleType.VAN, tariff("250.00", "140.00", "7.00", "500.00")));

        calculator = new DistanceTimeFareCalculator(properties);
    }

    // --- The formula --------------------------------------------------------

    @Test
    @DisplayName("CAR: the breakdown is base + perKm x distance + perMin x duration")
    void calculate_car_appliesFormulaExactly() {
        // 10 km, 24 min: 150 + (100 x 10) + (5 x 24) = 150 + 1000 + 120 = 1270
        FareBreakdown breakdown = calculator.calculate(VehicleType.CAR, new BigDecimal("10.00"), 24);

        assertThat(breakdown.baseFare()).isEqualByComparingTo("150.00");
        assertThat(breakdown.distanceFare()).isEqualByComparingTo("1000.00");
        assertThat(breakdown.timeFare()).isEqualByComparingTo("120.00");
        assertThat(breakdown.total()).isEqualByComparingTo("1270.00");
        assertThat(breakdown.minimumFareApplied()).isFalse();
    }

    @Test
    @DisplayName("TUK uses its own, cheaper tariff")
    void calculate_tuk_usesTukRates() {
        // 100 + (80 x 10) + (3 x 24) = 100 + 800 + 72 = 972
        FareBreakdown breakdown = calculator.calculate(VehicleType.TUK, new BigDecimal("10.00"), 24);

        assertThat(breakdown.total()).isEqualByComparingTo("972.00");
    }

    @Test
    @DisplayName("VAN uses its own, dearer tariff")
    void calculate_van_usesVanRates() {
        // 250 + (140 x 10) + (7 x 24) = 250 + 1400 + 168 = 1818
        FareBreakdown breakdown = calculator.calculate(VehicleType.VAN, new BigDecimal("10.00"), 24);

        assertThat(breakdown.total()).isEqualByComparingTo("1818.00");
    }

    // --- Minimum fare boundary ---------------------------------------------

    @Test
    @DisplayName("a very short TUK trip is charged the minimum, and says so")
    void calculate_belowMinimum_chargesMinimumAndFlagsIt() {
        // 100 + (80 x 0.1) + (3 x 1) = 100 + 8 + 3 = 111, which is under the 200 minimum.
        FareBreakdown breakdown = calculator.calculate(VehicleType.TUK, new BigDecimal("0.10"), 1);

        assertThat(breakdown.total()).isEqualByComparingTo("200.00");
        // The flag is what lets a receipt explain why the parts do not sum to the total.
        assertThat(breakdown.minimumFareApplied()).isTrue();
    }

    @Test
    @DisplayName("exactly at the minimum, the minimum is not treated as applied (boundary)")
    void calculate_exactlyAtMinimum_doesNotFlagMinimumApplied() {
        // TUK: 100 + (80 x 1.1) + (3 x 4) = 100 + 88 + 12 = 200.00, exactly the minimum.
        FareBreakdown breakdown = calculator.calculate(VehicleType.TUK, new BigDecimal("1.10"), 4);

        assertThat(breakdown.total()).isEqualByComparingTo("200.00");
        // The rule is "charge the minimum when the fare is below it"; equal is not below.
        assertThat(breakdown.minimumFareApplied()).isFalse();
    }

    @Test
    @DisplayName("one cent above the minimum, the computed fare is charged (boundary)")
    void calculate_justAboveMinimum_chargesComputedFare() {
        // TUK: 100 + (80 x 1.1) + (3 x 5) = 100 + 88 + 15 = 203.00
        FareBreakdown breakdown = calculator.calculate(VehicleType.TUK, new BigDecimal("1.10"), 5);

        assertThat(breakdown.total()).isEqualByComparingTo("203.00");
        assertThat(breakdown.minimumFareApplied()).isFalse();
    }

    @ParameterizedTest(name = "{0} minimum fare is {1}")
    @CsvSource({"TUK,200.00", "CAR,300.00", "VAN,500.00"})
    @DisplayName("a zero-distance trip falls back to each vehicle's minimum")
    void calculate_zeroDistance_chargesMinimum(VehicleType vehicleType, String expectedMinimum) {
        FareBreakdown breakdown = calculator.calculate(vehicleType, BigDecimal.ZERO, 1);

        // Guards the degenerate case: a fare must never be zero or negative.
        assertThat(breakdown.total()).isEqualByComparingTo(expectedMinimum);
        assertThat(breakdown.total()).isPositive();
    }

    // --- Rounding -----------------------------------------------------------

    @Test
    @DisplayName("money is rounded HALF_UP to exactly two decimal places")
    void calculate_roundsMoneyToTwoDecimalsHalfUp() {
        FareBreakdown breakdown = calculator.calculate(VehicleType.CAR, new BigDecimal("3.456"), 7);

        assertThat(breakdown.total().scale()).isEqualTo(2);
        // 3.456 km rounds to 3.46 before pricing, so distance fare is exactly 346.00.
        assertThat(breakdown.distanceKm()).isEqualByComparingTo("3.46");
        assertThat(breakdown.distanceFare()).isEqualByComparingTo("346.00");
    }

    // --- Estimates ----------------------------------------------------------

    @Test
    @DisplayName("an estimate inflates the straight-line distance by the winding factor")
    void estimate_appliesRoadWindingFactor() {
        // 10 km straight line x 1.3 = 13.00 km of road.
        FareBreakdown breakdown = calculator.estimate(VehicleType.CAR, 10.0);

        assertThat(breakdown.distanceKm()).isEqualByComparingTo("13.00");
    }

    @Test
    @DisplayName("an estimate derives duration from the assumed average speed, rounded up")
    void estimate_derivesDurationFromAverageSpeed() {
        // 13 km at 25 km/h = 31.2 minutes, rounded up to 32.
        FareBreakdown breakdown = calculator.estimate(VehicleType.CAR, 10.0);

        // Rounding up means a journey is never billed for less time than it takes.
        assertThat(breakdown.durationMin()).isEqualTo(32);
    }

    @Test
    @DisplayName("a zero-distance estimate still bills at least one minute")
    void estimate_zeroDistance_hasMinimumOneMinute() {
        FareBreakdown breakdown = calculator.estimate(VehicleType.CAR, 0.0);

        // Without the floor, the time component of a very short hop would be zero.
        assertThat(breakdown.durationMin()).isEqualTo(1);
        assertThat(breakdown.total()).isEqualByComparingTo("300.00");
    }

    @Test
    @DisplayName("a full Colombo to Negombo estimate prices correctly end to end")
    void estimate_realisticJourney_pricesCorrectly() {
        // ~30.5 km straight line x 1.3 = 39.65 km; 39.65 / 25 x 60 = 95.16 -> 96 min.
        // 150 + (100 x 39.65) + (5 x 96) = 150 + 3965 + 480 = 4595.00
        FareBreakdown breakdown = calculator.estimate(VehicleType.CAR, 30.5);

        assertThat(breakdown.distanceKm()).isEqualByComparingTo("39.65");
        assertThat(breakdown.durationMin()).isEqualTo(96);
        assertThat(breakdown.total()).isEqualByComparingTo("4595.00");
    }

    // --- Configuration ------------------------------------------------------

    @Test
    @DisplayName("a missing tariff fails loudly rather than pricing at zero")
    void calculate_missingTariff_throwsIllegalState() {
        RideLinkProperties incomplete = new RideLinkProperties(
                new RideLinkProperties.Security("secret"),
                new RideLinkProperties.Fare("LKR", new BigDecimal("1.3"), new BigDecimal("25"),
                        15, new BigDecimal("100.00")),
                Map.of(VehicleType.CAR, tariff("150.00", "100.00", "5.00", "300.00")));

        DistanceTimeFareCalculator partial = new DistanceTimeFareCalculator(incomplete);

        // Silently defaulting would charge nothing for a whole vehicle class.
        assertThatThrownBy(() -> partial.calculate(VehicleType.VAN, BigDecimal.TEN, 10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No tariff configured");
    }

    private static RideLinkProperties.Tariff tariff(String base, String perKm, String perMin,
                                                    String minimum) {
        return new RideLinkProperties.Tariff(new BigDecimal(base), new BigDecimal(perKm),
                new BigDecimal(perMin), new BigDecimal(minimum));
    }
}
