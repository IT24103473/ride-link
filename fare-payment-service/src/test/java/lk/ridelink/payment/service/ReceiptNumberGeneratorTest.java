package lk.ridelink.payment.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;
import lk.ridelink.payment.repository.ReceiptCounterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Receipt numbering against a real database.
 *
 * <p>A persistence test rather than a Mockito one, because the properties that matter -
 * that numbers are gapless, unique and restart each year - are properties of the counter
 * row, which a mock would simply assert away.</p>
 */
@DataJpaTest
@ActiveProfiles("test")
class ReceiptNumberGeneratorTest {

    private static final Instant IN_2026 = Instant.parse("2026-09-28T10:00:00Z");

    @Autowired
    private ReceiptCounterRepository counterRepository;

    @Test
    @DisplayName("the first receipt of a year is numbered 000001 in the documented format")
    void next_firstOfYear_usesExpectedFormat() {
        ReceiptNumberGenerator generator = generatorAt(IN_2026);

        assertThat(generator.next()).isEqualTo("RL-2026-000001");
    }

    @Test
    @DisplayName("numbers increment by one with no gaps")
    void next_calledRepeatedly_incrementsWithoutGaps() {
        ReceiptNumberGenerator generator = generatorAt(IN_2026);

        List<String> numbers = IntStream.range(0, 5)
                .mapToObj(i -> generator.next())
                .toList();

        // Gaplessness is a finance requirement: a skipped number cannot be reconciled.
        assertThat(numbers).containsExactly(
                "RL-2026-000001", "RL-2026-000002", "RL-2026-000003",
                "RL-2026-000004", "RL-2026-000005");
    }

    @Test
    @DisplayName("every number is unique")
    void next_calledRepeatedly_producesNoDuplicates() {
        ReceiptNumberGenerator generator = generatorAt(IN_2026);

        List<String> numbers = IntStream.range(0, 50)
                .mapToObj(i -> generator.next())
                .toList();

        // A duplicate would make two charges indistinguishable on paper.
        assertThat(numbers).doesNotHaveDuplicates().hasSize(50);
    }

    @Test
    @DisplayName("numbering restarts at 000001 in a new year")
    void next_newYear_restartsNumbering() {
        generatorAt(IN_2026).next();
        generatorAt(IN_2026).next();

        ReceiptNumberGenerator in2027 = generatorAt(Instant.parse("2027-01-01T00:00:00Z"));

        // Each year gets its own counter row, which is exactly what a plain database
        // sequence could not express.
        assertThat(in2027.next()).isEqualTo("RL-2027-000001");
    }

    @Test
    @DisplayName("the year in the number comes from the clock, not from the counter value")
    void next_usesClockYear() {
        assertThat(generatorAt(Instant.parse("2030-06-15T12:00:00Z")).next())
                .startsWith("RL-2030-");
    }

    /**
     * Builds a generator pinned to a fixed instant, so the year-boundary behaviour is
     * deterministic rather than depending on when the suite happens to run.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    ReceiptNumberGenerator generatorAt(Instant instant) {
        return new ReceiptNumberGenerator(counterRepository, Clock.fixed(instant, ZoneOffset.UTC));
    }
}
