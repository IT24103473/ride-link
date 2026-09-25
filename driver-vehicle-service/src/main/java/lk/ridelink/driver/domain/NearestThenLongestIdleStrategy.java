package lk.ridelink.driver.domain;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The default dispatch policy: nearest driver first, and on a tie, whoever has been
 * waiting longest.
 *
 * <p>The distance rule is obvious - the nearest driver reaches the passenger soonest.
 * The tie-break is the part worth defending: without it, a driver parked next to a busy
 * pickup point would win every single request at that spot, and other drivers in the
 * same area would never be dispatched. Ordering ties by {@code availableSince} spreads
 * work across the fleet.</p>
 */
@Component
public class NearestThenLongestIdleStrategy implements DriverRankingStrategy {

    /**
     * Distances closer together than this are treated as equal, so the fairness tie-break
     * actually fires. Without a tolerance, two drivers 1 metre apart would be ordered
     * purely by floating-point noise and the waiting rule would never apply.
     */
    private static final double DISTANCE_TIE_TOLERANCE_KM = 0.01;

    @Override
    public List<DriverCandidate> rank(List<DriverCandidate> candidates) {
        return candidates.stream()
                .sorted(byDistanceThenLongestIdle())
                .toList();
    }

    private static Comparator<DriverCandidate> byDistanceThenLongestIdle() {
        return (left, right) -> {
            // Near-equal distances fall through to the fairness tie-break.
            if (Math.abs(left.distanceKm() - right.distanceKm()) > DISTANCE_TIE_TOLERANCE_KM) {
                return Double.compare(left.distanceKm(), right.distanceKm());
            }

            Instant leftSince = left.availableSince();
            Instant rightSince = right.availableSince();

            // A missing timestamp should never happen for an AVAILABLE driver, but it
            // must not throw during dispatch: treat it as "just became available", so
            // such a driver sorts last rather than jumping the queue.
            if (leftSince == null && rightSince == null) {
                return left.driverId().compareTo(right.driverId());
            }
            if (leftSince == null) {
                return 1;
            }
            if (rightSince == null) {
                return -1;
            }

            int byWaiting = leftSince.compareTo(rightSince);

            // Final tie-break on id keeps the ordering deterministic, which matters
            // because the Ride service retries candidates in the order given.
            return byWaiting != 0 ? byWaiting : left.driverId().compareTo(right.driverId());
        };
    }
}
