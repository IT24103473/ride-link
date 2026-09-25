package lk.ridelink.payment.service;

import java.util.UUID;
import lk.ridelink.payment.dto.PayRequest;
import lk.ridelink.payment.dto.PaymentResponse;
import lk.ridelink.payment.dto.ReceiptResponse;
import lk.ridelink.payment.dto.RidePaymentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Settling charges and reading them back. */
public interface PaymentService {

    /**
     * The final fare and payment for a ride.
     *
     * @throws lk.ridelink.payment.exception.NotFoundException with code PAYMENT_NOT_READY
     *         while the ride.completed event is still in flight - the caller should retry
     */
    RidePaymentResponse getForRide(UUID rideId);

    /**
     * Settles a payment.
     *
     * @throws lk.ridelink.payment.exception.PaymentDeclinedException simulated card decline (402)
     * @throws lk.ridelink.payment.exception.PaymentAlreadyCompletedException already paid (409)
     */
    PaymentResponse pay(UUID paymentId, PayRequest request);

    /** Available only once paid; 404 otherwise. */
    ReceiptResponse getReceipt(UUID paymentId);

    /** A passenger sees their own payments; an admin sees all. */
    Page<PaymentResponse> list(Pageable pageable);
}
