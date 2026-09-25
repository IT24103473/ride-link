package lk.ridelink.account.domain;

/**
 * Lifecycle state of an account.
 *
 * <p>Only {@link #ACTIVE} accounts may log in. The other two are distinguished so the
 * login endpoint can return a specific error code, and so a suspension can be lifted
 * while a deactivation is treated as final.</p>
 */
public enum AccountStatus {
    ACTIVE,
    SUSPENDED,
    DEACTIVATED
}
