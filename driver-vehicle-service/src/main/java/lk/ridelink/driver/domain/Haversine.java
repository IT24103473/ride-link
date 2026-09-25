package lk.ridelink.driver.domain;

/**
 * Great-circle distance between two points on the Earth.
 *
 * <p>This is a straight-line ("as the crow flies") distance, not a road distance. Using a
 * real routing engine is out of scope for a simulated system, and the approximation is
 * honest: the Fare service multiplies its own straight-line distance by 1.3 to
 * approximate road winding, and matching only needs distances that rank correctly
 * relative to each other, not absolute accuracy.</p>
 *
 * <p>A static utility rather than a bean: it is a pure function of its inputs, with no
 * state and nothing to inject, so making it injectable would add indirection for nothing.</p>
 */
public final class Haversine {

    /** Mean Earth radius in kilometres. */
    private static final double EARTH_RADIUS_KM = 6371.0;

    private Haversine() {
    }

    /**
     * Distance in kilometres between two coordinates.
     *
     * @return a non-negative distance; zero when the two points are identical
     */
    public static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);

        // Standard haversine formula. Written with Math.sin(x/2) squared rather than a
        // cosine difference because it stays numerically stable for very small distances,
        // which is exactly the case when two drivers are near the same pickup point.
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad) * Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c;
    }
}
