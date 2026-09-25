package lk.ridelink.ride.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import lk.ridelink.ride.exception.InvalidRideTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The ride state machine: every legal move, and the illegal ones that matter.
 *
 * <p>Tested exhaustively rather than by example, because this is the rule that stops the
 * system from reaching states that make no physical sense - a completed ride being
 * cancelled, or a trip starting before a driver accepted. A gap here would not fail
 * loudly; it would simply allow nonsense.</p>
 */
class RideStatusTest {

    // --- Legal transitions --------------------------------------------------

    @ParameterizedTest(name = "{0} -> {1} is allowed")
    @CsvSource({
            "REQUESTED,ASSIGNED",
            "REQUESTED,CANCELLED",
            "ASSIGNED,ACCEPTED",
            "ASSIGNED,REQUESTED",
            "ASSIGNED,CANCELLED",
            "ACCEPTED,IN_PROGRESS",
            "ACCEPTED,CANCELLED",
            "IN_PROGRESS,COMPLETED"
    })
    @DisplayName("every documented transition is permitted")
    void canTransitionTo_documentedTransitions_areAllowed(RideStatus from, RideStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    // --- Illegal transitions ------------------------------------------------

    @Test
    @DisplayName("an IN_PROGRESS ride cannot be cancelled")
    void canTransitionTo_inProgressToCancelled_isRefused() {
        // The passenger is already in the vehicle, so cancellation is not a meaningful
        // action; the trip is completed or handled out of band.
        assertThat(RideStatus.IN_PROGRESS.canTransitionTo(RideStatus.CANCELLED)).isFalse();
    }

    @ParameterizedTest(name = "COMPLETED -> {0} is refused")
    @EnumSource(RideStatus.class)
    @DisplayName("COMPLETED is terminal: nothing follows it")
    void canTransitionTo_fromCompleted_isAlwaysRefused(RideStatus target) {
        assertThat(RideStatus.COMPLETED.canTransitionTo(target)).isFalse();
    }

    @ParameterizedTest(name = "CANCELLED -> {0} is refused")
    @EnumSource(RideStatus.class)
    @DisplayName("CANCELLED is terminal: nothing follows it")
    void canTransitionTo_fromCancelled_isAlwaysRefused(RideStatus target) {
        assertThat(RideStatus.CANCELLED.canTransitionTo(target)).isFalse();
    }

    @ParameterizedTest(name = "{0} -> {1} is refused")
    @CsvSource({
            // Skipping assignment entirely.
            "REQUESTED,ACCEPTED",
            "REQUESTED,IN_PROGRESS",
            "REQUESTED,COMPLETED",
            // Starting before the driver accepted.
            "ASSIGNED,IN_PROGRESS",
            "ASSIGNED,COMPLETED",
            // Completing without ever starting.
            "ACCEPTED,COMPLETED",
            // Going backwards.
            "ACCEPTED,ASSIGNED",
            "IN_PROGRESS,ACCEPTED",
            "IN_PROGRESS,REQUESTED"
    })
    @DisplayName("steps that skip stages or run backwards are refused")
    void canTransitionTo_illegalMoves_areRefused(RideStatus from, RideStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();
    }

    @ParameterizedTest(name = "{0} cannot transition to itself")
    @EnumSource(RideStatus.class)
    @DisplayName("no status transitions to itself")
    void canTransitionTo_sameStatus_isRefused(RideStatus status) {
        // Re-accepting or re-completing would double-publish the corresponding event.
        assertThat(status.canTransitionTo(status)).isFalse();
    }

    // --- Classification -----------------------------------------------------

    @Test
    @DisplayName("only COMPLETED and CANCELLED are terminal")
    void isTerminal_identifiesTheTwoEndStates() {
        assertThat(RideStatus.COMPLETED.isTerminal()).isTrue();
        assertThat(RideStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(RideStatus.REQUESTED.isTerminal()).isFalse();
        assertThat(RideStatus.ASSIGNED.isTerminal()).isFalse();
        assertThat(RideStatus.ACCEPTED.isTerminal()).isFalse();
        assertThat(RideStatus.IN_PROGRESS.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("a ride is active exactly while it is not terminal")
    void isActive_isTheInverseOfTerminal() {
        // This is what the one-active-ride-per-passenger rule keys on.
        for (RideStatus status : RideStatus.values()) {
            assertThat(status.isActive()).isEqualTo(!status.isTerminal());
        }
    }

    // --- Enforcement on the entity -----------------------------------------

    @Test
    @DisplayName("Ride.transitionTo refuses an illegal move with a 409 naming both states")
    void transitionTo_illegalMove_throwsWithBothStatesNamed() {
        Ride ride = completedRide();

        assertThatThrownBy(() -> ride.transitionTo(RideStatus.CANCELLED))
                .isInstanceOf(InvalidRideTransitionException.class)
                .hasMessageContaining("COMPLETED")
                .hasMessageContaining("CANCELLED")
                .hasMessageContaining("terminal");

        // The refused move must leave the ride untouched.
        assertThat(ride.getStatus()).isEqualTo(RideStatus.COMPLETED);
    }

    @Test
    @DisplayName("a legal transition stamps the matching timestamp")
    void transitionTo_legalMove_stampsTimestamp() {
        Ride ride = newRide();

        ride.transitionTo(RideStatus.ASSIGNED);
        assertThat(ride.getAssignedAt()).isNotNull();

        ride.transitionTo(RideStatus.ACCEPTED);
        assertThat(ride.getAcceptedAt()).isNotNull();

        ride.transitionTo(RideStatus.IN_PROGRESS);
        assertThat(ride.getStartedAt()).isNotNull();

        ride.transitionTo(RideStatus.COMPLETED);
        assertThat(ride.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("completing a ride moves the payment read model to PENDING")
    void transitionTo_completed_marksPaymentPending() {
        Ride ride = newRide();
        ride.transitionTo(RideStatus.ASSIGNED);
        ride.transitionTo(RideStatus.ACCEPTED);
        ride.transitionTo(RideStatus.IN_PROGRESS);

        assertThat(ride.getPaymentStatus()).isEqualTo(PaymentStatus.NOT_DUE);

        ride.transitionTo(RideStatus.COMPLETED);

        // Money is owed the instant the trip ends, before Payment has seen the event.
        assertThat(ride.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("a rejection clears the driver so the ride looks freshly requested")
    void transitionTo_assignedBackToRequested_clearsTheAssignment() {
        Ride ride = newRide();
        ride.assignDriver(UUID.randomUUID());
        ride.transitionTo(RideStatus.ASSIGNED);

        ride.transitionTo(RideStatus.REQUESTED);

        // Otherwise the next assignment attempt would see a stale driver still attached.
        assertThat(ride.getDriverId()).isNull();
        assertThat(ride.getAssignedAt()).isNull();
        assertThat(ride.getStatus()).isEqualTo(RideStatus.REQUESTED);
    }

    // --- Helpers ------------------------------------------------------------

    private static Ride newRide() {
        return Ride.request(UUID.randomUUID(),
                new Location("Colombo Fort", 6.9344, 79.8428),
                new Location("Negombo", 7.2083, 79.8358),
                VehicleType.CAR, ServiceArea.NEGOMBO, PaymentMethod.CARD);
    }

    private static Ride completedRide() {
        Ride ride = newRide();
        ride.transitionTo(RideStatus.ASSIGNED);
        ride.transitionTo(RideStatus.ACCEPTED);
        ride.transitionTo(RideStatus.IN_PROGRESS);
        ride.transitionTo(RideStatus.COMPLETED);
        return ride;
    }
}
