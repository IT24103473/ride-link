package lk.ridelink.payment.service;

import java.util.UUID;
import lk.ridelink.payment.domain.FinalFare;
import lk.ridelink.payment.domain.Payment;
import lk.ridelink.payment.domain.PaymentAttempt;
import lk.ridelink.payment.domain.PaymentMethod;
import lk.ridelink.payment.dto.PayRequest;
import lk.ridelink.payment.dto.PaymentResponse;
import lk.ridelink.payment.dto.ReceiptResponse;
import lk.ridelink.payment.dto.RidePaymentResponse;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private final PaymentRepository paymentRepository;
    private final FinalFareRepository finalFareRepository;
    private final PaymentAttemptRepository attemptRepository;
    private final SimulatedCardGateway cardGateway;
    private final ReceiptNumberGenerator receiptNumberGenerator;
    private final PaymentMapper mapper;
    private final EventPublisher eventPublisher;
    private final CurrentUser currentUser;

    public PaymentServiceImpl(PaymentRepository paymentRepository,
                              FinalFareRepository finalFareRepository,
                              PaymentAttemptRepository attemptRepository,
                              SimulatedCardGateway cardGateway,
                              ReceiptNumberGenerator receiptNumberGenerator,
                              PaymentMapper mapper,
                              EventPublisher eventPublisher,
                              CurrentUser currentUser) {
        this.paymentRepository = paymentRepository;
        this.finalFareRepository = finalFareRepository;
        this.attemptRepository = attemptRepository;
        this.cardGateway = cardGateway;
        this.receiptNumberGenerator = receiptNumberGenerator;
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
        this.currentUser = currentUser;
    }

    @Override
    @Transactional(readOnly = true)
    public RidePaymentResponse getForRide(UUID rideId) {
        Payment payment = paymentRepository.findByRideId(rideId)
                // Deliberately a distinct code: the ride may be perfectly valid and the
                // event simply still in flight, so the client should poll rather than stop.
                .orElseThrow(() -> NotFoundException.paymentNotReady(rideId));

        requireParticipantOrAdmin(payment);

        FinalFare fare = finalFareRepository.findById(payment.getFinalFareId())
                .orElseThrow(() -> NotFoundException.paymentNotReady(rideId));

        return new RidePaymentResponse(mapper.toFinalFare(fare), mapper.toPayment(payment));
    }

    @Override
    @Transactional
    public PaymentResponse pay(UUID paymentId, PayRequest request) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> NotFoundException.payment(paymentId));

        // Only the passenger pays. A driver may read the payment but must not settle it.
        if (!payment.getPassengerId().equals(currentUser.id())) {
            throw new AccessDeniedException("Only the ride's passenger may pay for it");
        }

        // Checked before doing anything, so a double submission is reported rather than
        // silently appearing to charge again. The unique receipt number and the version
        // column back this up if two requests arrive at once.
        if (payment.isPaid()) {
            throw new PaymentAlreadyCompletedException(paymentId);
        }

        PaymentMethod method = request.method();

        if (method == PaymentMethod.CASH) {
            // The money changed hands in the vehicle; this service only records it.
            return succeed(payment, method);
        }

        // An unknown token throws before anything is recorded: a malformed request must
        // not leave a FAILED payment behind, only a genuine decline should.
        SimulatedCardGateway.Outcome outcome = cardGateway.authorise(request.cardToken());

        if (outcome.approved()) {
            return succeed(payment, method);
        }

        payment.markFailed(outcome.failureReason(), method);
        paymentRepository.save(payment);
        attemptRepository.save(PaymentAttempt.failed(payment.getId(), method, outcome.failureReason()));

        log.info("Payment {} for ride {} failed: {}",
                payment.getId(), payment.getRideId(), outcome.failureReason());

        eventPublisher.publish(EventTypes.PAYMENT_FAILED, new PaymentFailedEvent(
                payment.getId(), payment.getRideId(), payment.getAmount(),
                payment.getCurrency(), outcome.failureReason().name()));

        // Thrown rather than returned, so the caller gets a 402 and cannot mistake a
        // decline for a successful settlement.
        throw new PaymentDeclinedException(outcome.failureReason().name());
    }

    private PaymentResponse succeed(Payment payment, PaymentMethod method) {
        // Allocated inside this transaction, so a rollback cannot leave a gap in the
        // receipt sequence.
        String receiptNumber = receiptNumberGenerator.next();

        payment.markSucceeded(receiptNumber, method);
        paymentRepository.save(payment);
        attemptRepository.save(PaymentAttempt.succeeded(payment.getId(), method));

        log.info("Payment {} for ride {} succeeded, receipt {}",
                payment.getId(), payment.getRideId(), receiptNumber);

        // Lets the Ride service update its read-only paymentStatus copy without polling.
        eventPublisher.publish(EventTypes.PAYMENT_SUCCEEDED, new PaymentSucceededEvent(
                payment.getId(), payment.getRideId(), payment.getAmount(),
                payment.getCurrency(), receiptNumber, payment.getPaidAt()));

        return mapper.toPayment(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public ReceiptResponse getReceipt(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> NotFoundException.payment(paymentId));

        requireParticipantOrAdmin(payment);

        // A receipt is proof of payment, so an unpaid or failed charge has none.
        if (!payment.isPaid()) {
            throw NotFoundException.receipt(paymentId);
        }

        FinalFare fare = finalFareRepository.findById(payment.getFinalFareId())
                .orElseThrow(() -> NotFoundException.payment(paymentId));

        return mapper.toReceipt(payment, fare);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentResponse> list(Pageable pageable) {
        Page<Payment> payments = currentUser.isAdmin()
                ? paymentRepository.findAll(pageable)
                : paymentRepository.findByPassengerId(currentUser.id(), pageable);

        return payments.map(mapper::toPayment);
    }

    /**
     * Restricts reads to the people actually involved in the ride.
     *
     * <p>The driver is included because they need to confirm they were paid, but a
     * passenger unrelated to the ride must not be able to read what someone else was
     * charged.</p>
     */
    private void requireParticipantOrAdmin(Payment payment) {
        if (currentUser.isAdmin()) {
            return;
        }
        if (!payment.belongsTo(currentUser.id())) {
            throw new AccessDeniedException("You were not involved in this ride");
        }
    }
}
