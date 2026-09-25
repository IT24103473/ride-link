package lk.ridelink.driver.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Distance calculation, pinned against known Sri Lankan coordinates.
 *
 * <p>Worth testing directly because every dispatch decision depends on it, and an error
 * here would be invisible in the API: drivers would simply be ranked oddly, with nothing
 * obviously broken.</p>
 */
class HaversineTest {

    // Real coordinates, so the expected distances can be checked against a map.
    private static final double COLOMBO_FORT_LAT = 6.9344;
    private static final double COLOMBO_FORT_LNG = 79.8428;
    private static final double NEGOMBO_LAT = 7.2083;
    private static final double NEGOMBO_LNG = 79.8358;
    private static final double KANDY_LAT = 7.2906;
    private static final double KANDY_LNG = 80.6337;

    @Test
    @DisplayName("Colombo Fort to Negombo is about 30 km in a straight line")
    void distanceKm_colomboToNegombo_matchesKnownDistance() {
        double distance = Haversine.distanceKm(
                COLOMBO_FORT_LAT, COLOMBO_FORT_LNG, NEGOMBO_LAT, NEGOMBO_LNG);

        // Straight line, not road distance; a generous tolerance keeps the test about
        // correctness of the formula rather than the precision of the coordinates.
        assertThat(distance).isCloseTo(30.5, within(2.0));
    }

    @Test
    @DisplayName("Colombo Fort to Kandy is about 94 km in a straight line")
    void distanceKm_colomboToKandy_matchesKnownDistance() {
        double distance = Haversine.distanceKm(COLOMBO_FORT_LAT, COLOMBO_FORT_LNG, KANDY_LAT, KANDY_LNG);

        assertThat(distance).isCloseTo(94.0, within(3.0));
    }

    @Test
    @DisplayName("the distance from a point to itself is zero")
    void distanceKm_samePoint_isZero() {
        double distance = Haversine.distanceKm(
                COLOMBO_FORT_LAT, COLOMBO_FORT_LNG, COLOMBO_FORT_LAT, COLOMBO_FORT_LNG);

        assertThat(distance).isZero();
    }

    @Test
    @DisplayName("distance is symmetric: A to B equals B to A")
    void distanceKm_isSymmetric() {
        double there = Haversine.distanceKm(COLOMBO_FORT_LAT, COLOMBO_FORT_LNG, NEGOMBO_LAT, NEGOMBO_LNG);
        double back = Haversine.distanceKm(NEGOMBO_LAT, NEGOMBO_LNG, COLOMBO_FORT_LAT, COLOMBO_FORT_LNG);

        assertThat(there).isEqualTo(back);
    }

    @Test
    @DisplayName("very small distances stay accurate rather than collapsing to zero")
    void distanceKm_veryClosePoints_staysAccurate() {
        // Roughly 111 metres north: 0.001 degrees of latitude.
        double distance = Haversine.distanceKm(
                COLOMBO_FORT_LAT, COLOMBO_FORT_LNG, COLOMBO_FORT_LAT + 0.001, COLOMBO_FORT_LNG);

        // This is the case that matters for ranking drivers around one pickup point, and
        // it is where a naive cosine-based formula loses precision.
        assertThat(distance).isCloseTo(0.111, within(0.005));
    }

    @Test
    @DisplayName("distance is never negative, whichever hemisphere the points are in")
    void distanceKm_acrossHemispheres_isPositive() {
        assertThat(Haversine.distanceKm(-33.8688, 151.2093, 51.5074, -0.1278)).isPositive();
    }

    @Test
    @DisplayName("a one degree change in latitude is about 111 km anywhere on Earth")
    void distanceKm_oneDegreeLatitude_isAbout111Km() {
        assertThat(Haversine.distanceKm(0.0, 0.0, 1.0, 0.0)).isCloseTo(111.19, within(0.5));
    }
}
