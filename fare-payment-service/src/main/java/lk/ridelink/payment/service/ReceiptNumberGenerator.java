package lk.ridelink.payment.service;

import java.time.Clock;
import java.time.ZoneOffset;
import lk.ridelink.payment.domain.ReceiptCounter;
import lk.ridelink.payment.repository.ReceiptCounterRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Allocates receipt numbers of the form {@code RL-2026-000123}.
 *
 * <p>Receipts are numbered per year and must be gapless and unique, which is why this is a
 * locked counter rather than a random id or a timestamp: a finance record that skips
 * numbers cannot be reconciled, and a duplicate number would make two charges
 * indistinguishable.</p>
 */
@Component
public class ReceiptNumberGenerator {

    private static final String PREFIX = "RL";
    /** Six digits allows a million receipts a year, far beyond anything this system needs. */
    private static final String FORMAT = PREFIX + "-%d-%06d";

    private final ReceiptCounterRepository counterRepository;
    private final Clock clock;

    public ReceiptNumberGenerator(ReceiptCounterRepository counterRepository, Clock clock) {
        this.counterRepository = counterRepository;
        this.clock = clock;
    }

    /**
     * Takes the next receipt number for the current year.
     *
     * <p>Runs in the caller's transaction so the number and the payment it belongs to
     * commit together: a number handed out for a payment that then rolled back would leave
     * a permanent gap.</p>
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String next() {
        int year = clock.instant().atZone(ZoneOffset.UTC).getYear();

        // The pessimistic lock serialises concurrent allocations, so two payments
        // completing at the same instant cannot receive the same number.
        ReceiptCounter counter = counterRepository.findByYearForUpdate(year)
                .orElseGet(() -> counterRepository.save(new ReceiptCounter(year)));

        long sequence = counter.next();
        counterRepository.save(counter);

        return FORMAT.formatted(year, sequence);
    }
}
