package lk.ridelink.payment.domain;

/** Result of one payment attempt, recorded for the audit trail. */
public enum PaymentOutcome {
    SUCCEEDED,
    FAILED
}
