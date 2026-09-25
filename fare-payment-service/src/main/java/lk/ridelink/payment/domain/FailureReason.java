package lk.ridelink.payment.domain;

/**
 * Why a simulated payment failed.
 *
 * <p>Deliberately a small closed set: these are the outcomes the fake card tokens map to,
 * and they are what the {@code payment.failed} event carries.</p>
 */
public enum FailureReason {
    CARD_DECLINED,
    INSUFFICIENT_FUNDS
}
