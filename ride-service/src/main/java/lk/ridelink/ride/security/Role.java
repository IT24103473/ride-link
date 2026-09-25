package lk.ridelink.ride.security;

/**
 * Roles this service recognises in the {@code role} JWT claim.
 *
 * <p>Declared locally rather than imported from the Account Service: the two must agree
 * on the spelling, but they must not share a compiled artifact, or the services stop
 * being independently deployable.</p>
 */
public enum Role {
    PASSENGER,
    DRIVER,
    ADMIN,
    /** Only a SERVICE token may call {@code /api/v1/internal/**}. */
    SERVICE
}
