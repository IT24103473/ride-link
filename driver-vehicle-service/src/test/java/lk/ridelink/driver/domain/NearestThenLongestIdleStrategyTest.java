package lk.ridelink.driver.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ranking rule: nearest first, longest-waiting on a tie.
 *
 * <p>The tie-break is the part worth pinning down. It is a fairness rule, so it has no
 * visible symptom when it breaks - rides would simply concentrate on one driver, which
 * nobody would notice during a demo.</p>
 */
class NearestThenLongestIdleStrategyTest {

    private final NearestThenLongestIdleStrategy strategy = new NearestThenLongestIdleStrategy();

    private static final Instant NOW = Instant.parse("2026-09-28T10:00:00Z");

    @Test
    @DisplayName("ranks strictly by distance when distances differ")
    void rank_differingDistances_ordersNearestFirst() {
        DriverCandidate far = candidate("far", 8.0, NOW.minus(30, ChronoUnit.MINUTES));
        DriverCandidate near = candidate("near", 1.2, NOW);
        DriverCandidate middle = candidate("middle", 4.5, NOW.minus(10, ChronoUnit.MINUTES));

        List<DriverCandidate> ranked = strategy.rank(List.of(far, near, middle));

        assertThat(ranked).extracting(DriverCandidate::fullName)
                .containsExactly("near", "middle", "far");
    }

    @Test
    @DisplayName("on equal distance the driver waiting longest is ranked first")
    void rank_equalDistance_prefersLongestWaiting() {
        DriverCandidate justArrived = candidate("just-arrived", 2.0, NOW);
        DriverCandidate waitingLongest = candidate("waiting-longest", 2.0, NOW.minus(45, ChronoUnit.MINUTES));
        DriverCandidate waitingAWhile = candidate("waiting-a-while", 2.0, NOW.minus(15, ChronoUnit.MINUTES));

        List<DriverCandidate> ranked = strategy.rank(
                List.of(justArrived, waitingLongest, waitingAWhile));

        // Without this rule, a driver parked at a busy pickup point would take every ride.
        assertThat(ranked).extracting(DriverCandidate::fullName)
                .containsExactly("waiting-longest", "waiting-a-while", "just-arrived");
    }

    @Test
    @DisplayName("distances within the tie tolerance are treated as equal, so fairness applies")
    void rank_nearlyEqualDistance_stillUsesWaitingTieBreak() {
        // 5 metres apart: far too close to justify ignoring the fairness rule.
        DriverCandidate slightlyNearer = candidate("slightly-nearer", 2.000, NOW);
        DriverCandidate waitingLonger = candidate("waiting-longer", 2.005, NOW.minus(40, ChronoUnit.MINUTES));

        List<DriverCandidate> ranked = strategy.rank(List.of(slightlyNearer, waitingLonger));

        assertThat(ranked).first()
                .extracting(DriverCandidate::fullName)
                .isEqualTo("waiting-longer");
    }

    @Test
    @DisplayName("a distance gap wider than the tolerance beats the waiting tie-break")
    void rank_clearlyNearer_winsOverLongerWaiting() {
        DriverCandidate clearlyNearer = candidate("clearly-nearer", 1.0, NOW);
        DriverCandidate waitingLonger = candidate("waiting-longer", 3.0, NOW.minus(60, ChronoUnit.MINUTES));

        List<DriverCandidate> ranked = strategy.rank(List.of(clearlyNearer, waitingLonger));

        // Fairness must not override getting to the passenger quickly.
        assertThat(ranked).first()
                .extracting(DriverCandidate::fullName)
                .isEqualTo("clearly-nearer");
    }

    @Test
    @DisplayName("ranking is deterministic when distance and waiting time are both equal")
    void rank_completelyTied_isDeterministic() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");

        DriverCandidate a = new DriverCandidate(second, "b", 2.0, NOW, VehicleType.CAR, "WP-2", 0);
        DriverCandidate b = new DriverCandidate(first, "a", 2.0, NOW, VehicleType.CAR, "WP-1", 0);

        // The caller retries candidates in the order given, so a stable order matters:
        // a shuffling list would make the assignment flow unreproducible.
        assertThat(strategy.rank(List.of(a, b))).isEqualTo(strategy.rank(List.of(b, a)));
    }

    @Test
    @DisplayName("a null availableSince sorts last rather than throwing")
    void rank_missingAvailableSince_sortsLastWithoutFailing() {
        DriverCandidate missing = candidate("missing-timestamp", 2.0, null);
        DriverCandidate present = candidate("has-timestamp", 2.0, NOW);

        List<DriverCandidate> ranked = strategy.rank(List.of(missing, present));

        // Should never happen for an AVAILABLE driver, but dispatch must not crash on it.
        assertThat(ranked).extracting(DriverCandidate::fullName)
                .containsExactly("has-timestamp", "missing-timestamp");
    }

    @Test
    @DisplayName("an empty candidate list ranks to an empty list")
    void rank_emptyList_returnsEmpty() {
        assertThat(strategy.rank(List.of())).isEmpty();
    }

    @Test
    @DisplayName("ranking does not modify the list it was given")
    void rank_doesNotMutateInput() {
        DriverCandidate far = candidate("far", 9.0, NOW);
        DriverCandidate near = candidate("near", 1.0, NOW);
        List<DriverCandidate> input = List.of(far, near);

        strategy.rank(input);

        assertThat(input).containsExactly(far, near);
    }

    private static DriverCandidate candidate(String name, double distanceKm, Instant availableSince) {
        return new DriverCandidate(UUID.randomUUID(), name, distanceKm, availableSince,
                VehicleType.CAR, "WP CAB-0000", 0);
    }
}
