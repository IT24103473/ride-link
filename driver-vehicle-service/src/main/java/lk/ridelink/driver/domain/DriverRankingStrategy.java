package lk.ridelink.driver.domain;

import java.util.List;

/**
 * Decides which eligible driver should be offered a ride first.
 *
 * <p>An interface rather than a method on the matching service, because dispatch policy
 * is the part of this system most likely to change: a future rule might prefer the
 * highest-rated driver, the one with fewest trips today, or a surge-aware choice. Each
 * of those is a new implementation, not an edit to the caller - the Open/Closed example
 * cited in the report.</p>
 */
public interface DriverRankingStrategy {

    /**
     * Orders candidates best-first.
     *
     * @param candidates already filtered for eligibility; never null
     * @return a new ordered list; the input is not modified
     */
    List<DriverCandidate> rank(List<DriverCandidate> candidates);
}
