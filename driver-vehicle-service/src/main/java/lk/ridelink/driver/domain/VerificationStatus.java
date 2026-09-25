package lk.ridelink.driver.domain;

/**
 * Whether an admin has checked the driver's licence.
 *
 * <p>A profile starts {@link #PENDING} when created from the
 * {@code account.driver.registered} event; only a {@link #VERIFIED} driver can go
 * online, so nobody is dispatched before a human has approved them.</p>
 */
public enum VerificationStatus {
    PENDING,
    VERIFIED,
    REJECTED
}
