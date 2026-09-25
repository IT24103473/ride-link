package lk.ridelink.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A per-year counter backing the receipt numbering.
 *
 * <p>A table rather than a database sequence, for two reasons: sequences differ between
 * MySQL and the H2 used in tests, and receipt numbers restart each year, which a plain
 * sequence cannot express.</p>
 *
 * <p>Rows are read with a pessimistic lock when allocating, so two concurrent payments
 * cannot be issued the same receipt number.</p>
 */
@Entity
@Table(name = "receipt_counter")
public class ReceiptCounter {

    /**
     * Column is {@code counter_year}, not {@code year}: YEAR is a reserved word in H2, so
     * a column called "year" works on MySQL but breaks the test schema.
     */
    @Id
    @Column(name = "counter_year", nullable = false, updatable = false)
    private int year;

    @Column(name = "last_value", nullable = false)
    private long lastValue;

    protected ReceiptCounter() {
        // Required by JPA.
    }

    public ReceiptCounter(int year) {
        this.year = year;
        this.lastValue = 0;
    }

    /** Takes the next number in sequence for this year. */
    public long next() {
        return ++lastValue;
    }

    public int getYear() {
        return year;
    }

    public long getLastValue() {
        return lastValue;
    }
}
