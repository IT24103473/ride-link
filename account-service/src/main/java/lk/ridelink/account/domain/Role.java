package lk.ridelink.account.domain;

/**
 * Authorisation role carried in the {@code role} JWT claim.
 *
 * <p>{@link #SERVICE} is not a human account: it is only ever issued by the
 * client-credentials endpoint so that one service can call another's
 * {@code /api/v1/internal/**} endpoints. No Account row is ever stored with it.</p>
 */
public enum Role {
    PASSENGER,
    DRIVER,
    ADMIN,
    SERVICE
}
