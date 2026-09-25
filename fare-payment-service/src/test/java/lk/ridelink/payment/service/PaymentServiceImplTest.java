package lk.ridelink.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import lk.ridelink.payment.domain.FareBreakdown;
import lk.ridelink.payment.domain.FareType;
import lk.ridelink.payment.domain.FinalFare;
import lk.ridelink.payment.domain.Payment;
import lk.ridelink.payment.domain.PaymentAttempt;
import lk.ridelink.payment.domain.PaymentMethod;
import lk.ridelink.payment.domain.PaymentStatus;
import lk.ridelink.payment.domain.VehicleType;
import lk.ridelink.payment.dto.PayRequest;
import lk.ridelink.payment.dto.PaymentResponse;
import lk.ridelink.payment.exception.InvalidRequestException;
import lk.ridelink.payment.exception.NotFoundException;
import lk.ridelink.payment.exception.PaymentAlreadyCompletedException;
import lk.ridelink.payment.exception.PaymentDeclinedException;
import lk.ridelink.payment.mapper.PaymentMapper;
import lk.ridelink.payment.messaging.EventPublisher;
import lk.ridelink.payment.messaging.EventTypes;
import lk.ridelink.payment.messaging.PaymentFailedEvent;
import lk.ridelink.payment.messaging.PaymentSucceededEvent;
import lk.ridelink.payment.repository.FinalFareRepository;
import lk.ridelink.payment.repository.PaymentAttemptRepository;
import lk.ridelink.payment.repository.PaymentRepository;
import lk.ridelink.payment.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

/**
 * The payment flow: every simulated token outcome, retry after failure, the double-pay
 * guard, and who is allowed to see or settle a charge.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    private static final BigDecimal AMOUNT = new BigDecimal("4485.00");
    private static final String RECEIPT = "RL-2026-000123";

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private FinalFareRepository finalFareRepository;
    @Mock
    private PaymentAttemptRepository attemptRepository;
    @Mock
    private ReceiptNumberGenerator receiptNumberGenerator;
    @Mock
    private EventPublisher eventPublisher;
    @Mock
    private CurrentUser currentUser;

    private PaymentServiceImpl paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentServiceImpl(paymentRepository, finalFareRepository,
                attemptRepository, new SimulatedCardGateway(), receiptNumberGenerator,
                new PaymentMapper(), eventPublisher, currentUser);
    }

    // --- Successful payment -------------------------------------------------

    @Test
    @DisplayName("CASH always succeeds and allocates a receipt number")
    void pay_cash_succeeds() {
        Payment payment = pendingPayment();
        arrangePayer(payment);
        when(receiptNumberGenerator.next()).thenReturn(RECEIPT);

        PaymentResponse response = paymentService.pay(payment.getId(),
                new PayRequest(PaymentMethod.CASH, null));

        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(response.receiptNumber()).isEqualTo(RECEIPT);
        assertThat(response.paidAt()).isNotNull();
    }

    @Test
    @DisplayName("CARD with tok_success succeeds")
    void pay_cardSuccessToken_succeeds() {
        Payment payment = pendingPayment();
        arrangePayer(payment);
        when(receiptNumberGenerator.next()).thenReturn(RECEIPT);

        PaymentResponse response = paymentService.pay(payment.getId(),
                new PayRequest(PaymentMethod.CARD, SimulatedCardGateway.TOKEN_SUCCESS));

        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(response.receiptNumber()).isEqualTo(RECEIPT);
    }

    @Test
    @DisplayName("a successful payment publishes payment.succeeded with the receipt number")
    void pay_success_publishesPaymentSucceeded() {
        Payment payment = pendingPayment();
        arrangePayer(payment);
        when(receiptNumberGenerator.next()).thenReturn(RECEIPT);

        paymentService.pay(payment.getId(), new PayRequest(PaymentMethod.CASH, null));

        ArgumentCaptor<PaymentSucceededEvent> event =
                ArgumentCaptor.forClass(PaymentSucceededEvent.class);
        verify(eventPublisher).publish(eq(EventTypes.PAYMENT_SUCCEEDED), event.capture());

        // Lets the Ride service update its paymentStatus copy without polling this service.
        assertThat(event.getValue().rideId()).isEqualTo(payment.getRideId());
        assertThat(event.getValue().receiptNumber()).isEqualTo(RECEIPT);
        assertThat(event.getValue().amount()).isEqualByComparingTo(AMOUNT);
    }

    @Test
    @DisplayName("a successful payment records a SUCCEEDED attempt")
    void pay_success_recordsAttempt() {
        Payment payment = pendingPayment();
        arrangePayer(payment);
        when(receiptNumberGenerator.next()).thenReturn(RECEIPT);

        paymentService.pay(payment.getId(), new PayRequest(PaymentMethod.CASH, null));

        verify(attemptRepository).save(any(PaymentAttempt.class));
    }

    // --- Declines -----------------------------------------------------------

    @Test
    @DisplayName("tok_declined gives 402 and leaves the payment FAILED, not PENDING")
    void pay_declinedToken_throws402AndMarksFailed() {
        Payment payment = pendingPayment();
        arrangePayer(payment);

        assertThatThrownBy(() -> paymentService.pay(payment.getId(),
                new PayRequest(PaymentMethod.CARD, SimulatedCardGateway.TOKEN_DECLINED)))
                .isInstanceOf(PaymentDeclinedException.class)
                .extracting(ex -> ((PaymentDeclinedException) ex).getFailureReason())
                .isEqualTo("CARD_DECLINED");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        // FAILED is not terminal: the passenger may try again.
        assertThat(payment.getReceiptNumber()).isNull();
        verify(receiptNumberGenerator, never()).next();
    }

    @Test
    @DisplayName("tok_insufficient_funds gives 402 with its own distinct reason")
    void pay_insufficientFundsToken_throws402WithSpecificReason() {
        Payment payment = pendingPayment();
        arrangePayer(payment);

        assertThatThrownBy(() -> paymentService.pay(payment.getId(),
                new PayRequest(PaymentMethod.CARD, SimulatedCardGateway.TOKEN_INSUFFICIENT_FUNDS)))
                .isInstanceOf(PaymentDeclinedException.class)
                .extracting(ex -> ((PaymentDeclinedException) ex).getFailureReason())
                .isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    @DisplayName("a decline publishes payment.failed")
    void pay_declined_publishesPaymentFailed() {
        Payment payment = pendingPayment();
        arrangePayer(payment);

        assertThatThrownBy(() -> paymentService.pay(payment.getId(),
                new PayRequest(PaymentMethod.CARD, SimulatedCardGateway.TOKEN_DECLINED)))
                .isInstanceOf(PaymentDeclinedException.class);

        ArgumentCaptor<PaymentFailedEvent> event = ArgumentCaptor.forClass(PaymentFailedEvent.class);
        verify(eventPublisher).publish(eq(EventTypes.PAYMENT_FAILED), event.capture());
        assertThat(event.getValue().failureReason()).isEqualTo("CARD_DECLINED");
    }

    @Test
    @DisplayName("an unknown token is a 400 and leaves no failed payment behind")
    void pay_unknownToken_throws400AndRecordsNothing() {
        Payment payment = pendingPayment();
        arrangePayer(payment);

        assertThatThrownBy(() -> paymentService.pay(payment.getId(),
                new PayRequest(PaymentMethod.CARD, "tok_made_up")))
                .isInstanceOf(InvalidRequestException.class);

        // The distinction matters: a malformed request is not a declined card, so it must
        // not leave a FAILED payment, an attempt record or an event.
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(attemptRepository, never()).save(any());
        verify(eventPublisher, never()).publish(anyString(), any());
    }

    @Test
    @DisplayName("CARD with no token at all is a 400")
    void pay_cardWithoutToken_throws400() {
        Payment payment = pendingPayment();
        arrangePayer(payment);

        assertThatThrownBy(() -> paymentService.pay(payment.getId(),
                new PayRequest(PaymentMethod.CARD, null)))
                .isInstanceOf(InvalidRequestException.class);
    }

    // --- Retry --------------------------------------------------------------

    @Test
    @DisplayName("a failed payment can be retried and then succeed")
    void pay_retryAfterFailure_succeeds() {
        Payment payment = pendingPayment();
        arrangePayer(payment);
        when(receiptNumberGenerator.next()).thenReturn(RECEIPT);

        assertThatThrownBy(() -> paymentService.pay(payment.getId(),
                new PayRequest(PaymentMethod.CARD, SimulatedCardGateway.TOKEN_DECLINED)))
                .isInstanceOf(PaymentDeclinedException.class);

        PaymentResponse response = paymentService.pay(payment.getId(),
                new PayRequest(PaymentMethod.CARD, SimulatedCardGateway.TOKEN_SUCCESS));

        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        // The stale failure reason must be cleared, or a paid ride would still look failed.
        assertThat(response.failureReason()).isNull();
        // Both tries are logged, which is what makes a disputed charge explainable.
        verify(attemptRepository, org.mockito.Mockito.times(2)).save(any(PaymentAttempt.class));
    }

    // --- Double payment -----------------------------------------------------

    @Test
    @DisplayName("paying an already-paid payment gives 409, not a second charge")
    void pay_alreadySucceeded_throws409() {
        Payment payment = pendingPayment();
        payment.markSucceeded(RECEIPT, PaymentMethod.CASH);
        arrangePayer(payment);

        assertThatThrownBy(() -> paymentService.pay(payment.getId(),
                new PayRequest(PaymentMethod.CASH, null)))
                .isInstanceOf(PaymentAlreadyCompletedException.class);

        verify(receiptNumberGenerator, never()).next();
        verify(eventPublisher, never()).publish(anyString(), any());
    }

    // --- Authorisation ------------------------------------------------------

    @Test
    @DisplayName("only the ride's passenger may pay")
    void pay_notThePassenger_throwsAccessDenied() {
        Payment payment = pendingPayment();
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(currentUser.id()).thenReturn(UUID.randomUUID());

        // A driver may read the payment but must not be able to settle it.
        assertThatThrownBy(() -> paymentService.pay(payment.getId(),
                new PayRequest(PaymentMethod.CASH, null)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("a stranger cannot read what someone else was charged")
    void getForRide_notAParticipant_throwsAccessDenied() {
        Payment payment = pendingPayment();
        when(paymentRepository.findByRideId(payment.getRideId())).thenReturn(Optional.of(payment));
        when(currentUser.isAdmin()).thenReturn(false);
        when(currentUser.id()).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> paymentService.getForRide(payment.getRideId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("the driver on the ride may read the payment, to confirm they were paid")
    void getForRide_driverOnTheRide_isAllowed() {
        Payment payment = pendingPayment();
        when(paymentRepository.findByRideId(payment.getRideId())).thenReturn(Optional.of(payment));
        when(currentUser.isAdmin()).thenReturn(false);
        when(currentUser.id()).thenReturn(payment.getDriverId());
        when(finalFareRepository.findById(payment.getFinalFareId()))
                .thenReturn(Optional.of(finalFare(payment.getRideId())));

        assertThat(paymentService.getForRide(payment.getRideId()).payment().id())
                .isEqualTo(payment.getId());
    }

    // --- Not ready / not found ---------------------------------------------

    @Test
    @DisplayName("a ride with no payment yet gives PAYMENT_NOT_READY, so clients retry")
    void getForRide_noPaymentYet_throwsPaymentNotReady() {
        UUID rideId = UUID.randomUUID();
        when(paymentRepository.findByRideId(rideId)).thenReturn(Optional.empty());

        // A distinct code from plain NOT_FOUND: the ride.completed event may still be in
        // flight, so the client should poll rather than give up.
        assertThatThrownBy(() -> paymentService.getForRide(rideId))
                .isInstanceOf(NotFoundException.class)
                .extracting(ex -> ((NotFoundException) ex).getCode())
                .isEqualTo("PAYMENT_NOT_READY");
    }

    // --- Receipts -----------------------------------------------------------

    @Test
    @DisplayName("a receipt is available once paid")
    void getReceipt_paidPayment_returnsReceipt() {
        Payment payment = pendingPayment();
        payment.markSucceeded(RECEIPT, PaymentMethod.CARD);
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(currentUser.isAdmin()).thenReturn(false);
        when(currentUser.id()).thenReturn(payment.getPassengerId());
        when(finalFareRepository.findById(payment.getFinalFareId()))
                .thenReturn(Optional.of(finalFare(payment.getRideId())));

        assertThat(paymentService.getReceipt(payment.getId()).receiptNumber()).isEqualTo(RECEIPT);
    }

    @Test
    @DisplayName("an unpaid payment has no receipt")
    void getReceipt_unpaidPayment_throwsNotFound() {
        Payment payment = pendingPayment();
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(currentUser.isAdmin()).thenReturn(false);
        when(currentUser.id()).thenReturn(payment.getPassengerId());

        // A receipt is proof of payment, so producing one for an unpaid charge would be
        // actively misleading.
        assertThatThrownBy(() -> paymentService.getReceipt(payment.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    // --- Helpers ------------------------------------------------------------

    private void arrangePayer(Payment payment) {
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(currentUser.id()).thenReturn(payment.getPassengerId());
    }

    private static Payment pendingPayment() {
        return Payment.pending(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), FareType.TRIP, AMOUNT, "LKR", PaymentMethod.CARD);
    }

    private static FinalFare finalFare(UUID rideId) {
        FareBreakdown breakdown = new FareBreakdown(new BigDecimal("39.65"), 96,
                new BigDecimal("150.00"), new BigDecimal("3965.00"), new BigDecimal("480.00"),
                new BigDecimal("300.00"), false, AMOUNT);
        return FinalFare.forTrip(rideId, VehicleType.CAR, breakdown, "LKR");
    }
}
