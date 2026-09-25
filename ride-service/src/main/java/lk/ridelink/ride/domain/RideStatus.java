package lk.ridelink.ride.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The ride lifecycle, together with the transitions that are legal between its states.
 *
 * <p>Holding the allowed-transitions map on the enum itself - rather than as {@code if}
 * checks scattered across the service methods - means there is exactly one definition of
 * what may follow what. Every endpoint funnels through
 * {@link Ride#transitionTo}, so no new endpoint can accidentally permit a move the state
 * machine forbids.</p>
 *
 * <p>Two consequences are worth stating because they are demonstrated as negative cases:
 * {@link #IN_PROGRESS} cannot be cancelled (the passenger is already in the vehicle, so
 * cancellation is not a meaningful action), and {@link #COMPLETED} and {@link #CANCELLED}
 * are terminal, so a finished ride can never be re-cancelled, re-started or re-paid.</p>
 */
public enum RideStatus {

    /** Created and priced, waiting for a driver to be found. */
    REQUESTED,

    /** A driver has been reserved but has not yet accepted. */
    ASSIGNED,

    /** The driver accepted and is travelling to the pickup point. */
    ACCEPTED,

    /** The passenger is in the vehicle. */
    IN_PROGRESS,

    /** Finished; the final fare is computed from here. Terminal. */
    COMPLETED,

    /** Called off before completion. Terminal. */
    CANCELLED;

    /**
     * The single source of truth for legal moves.
     *
     * <p>Note {@code ASSIGNED -> REQUESTED}: a driver rejecting a ride returns it to the
     * pool rather than cancelling it, so the passenger can be matched with someone else
     * without having to book again.</p>
     */
    private static final Map<RideStatus, Set<RideStatus>> ALLOWED = Map.of(
            REQUESTED, EnumSet.of(ASSIGNED, CANCELLED),
            ASSIGNED, EnumSet.of(ACCEPTED, REQUESTED, CANCELLED),
            ACCEPTED, EnumSet.of(IN_PROGRESS, CANCELLED),
            // Deliberately no CANCELLED here: a trip under way cannot be cancelled.
            IN_PROGRESS, EnumSet.of(COMPLETED),
            COMPLETED, EnumSet.noneOf(RideStatus.class),
            CANCELLED, EnumSet.noneOf(RideStatus.class));

    public boolean canTransitionTo(RideStatus target) {
        return ALLOWED.getOrDefault(this, EnumSet.noneOf(RideStatus.class)).contains(target);
    }

    /** Terminal states accept no further transitions. */
    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }

    /**
     * True while the ride still occupies the passenger, which is what enforces the
     * one-active-ride-per-passenger rule.
     */
    public boolean isActive() {
        return !isTerminal();
    }

    public Set<RideStatus> allowedTransitions() {
        return ALLOWED.getOrDefault(this, EnumSet.noneOf(RideStatus.class));
    }
}
