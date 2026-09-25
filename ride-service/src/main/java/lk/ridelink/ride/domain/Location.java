package lk.ridelink.ride.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** A place name with simulated coordinates. Embedded; it has no identity of its own. */
@Embeddable
public record Location(

        @Column(name = "name", nullable = false, length = 100)
        String name,

        @Column(name = "lat", nullable = false)
        Double lat,

        @Column(name = "lng", nullable = false)
        Double lng
) {

    /**
     * True when this is effectively the same place as {@code other}.
     *
     * <p>Compared on both name and coordinates: a ride from somewhere to itself would
     * travel no distance and bill only the minimum fare, so it is rejected at request time.
     */
    public boolean isSamePlaceAs(Location other) {
        if (other == null) {
            return false;
        }
        boolean sameName = name != null && name.equalsIgnoreCase(other.name());
        boolean sameCoordinates = lat.equals(other.lat()) && lng.equals(other.lng());
        return sameName || sameCoordinates;
    }
}
