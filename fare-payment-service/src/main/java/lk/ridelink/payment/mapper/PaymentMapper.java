package lk.ridelink.payment.mapper;

import lk.ridelink.payment.domain.FareBreakdown;
import lk.ridelink.payment.domain.FareEstimate;
import lk.ridelink.payment.domain.FinalFare;
import lk.ridelink.payment.domain.Location;
import lk.ridelink.payment.domain.Payment;
import lk.ridelink.payment.dto.FareBreakdownResponse;
import lk.ridelink.payment.dto.FareEstimateResponse;
import lk.ridelink.payment.dto.FinalFareResponse;
import lk.ridelink.payment.dto.LocationRequest;
import lk.ridelink.payment.dto.LocationResponse;
import lk.ridelink.payment.dto.PaymentResponse;
import lk.ridelink.payment.dto.ReceiptResponse;
import org.springframework.stereotype.Component;

/** Entity to DTO conversion for fares, payments and receipts. */
@Component
public class PaymentMapper {

    public Location toLocation(LocationRequest request) {
        return new Location(request.name(), request.lat(), request.lng());
    }

    public LocationResponse toLocationResponse(Location location) {
        return new LocationResponse(location.name(), location.lat(), location.lng());
    }

    public FareBreakdownResponse toBreakdown(FareBreakdown breakdown) {
        return new FareBreakdownResponse(
                breakdown.distanceKm(),
                breakdown.durationMin(),
                breakdown.baseFare(),
                breakdown.distanceFare(),
                breakdown.timeFare(),
                breakdown.minimumFare(),
                breakdown.minimumFareApplied(),
                breakdown.total());
    }

    public FareEstimateResponse toEstimate(FareEstimate estimate) {
        return new FareEstimateResponse(
                estimate.getId(),
                estimate.getPassengerId(),
                toLocationResponse(estimate.getPickup()),
                toLocationResponse(estimate.getDestination()),
                estimate.getVehicleType(),
                toBreakdown(estimate.getBreakdown()),
                estimate.getCurrency(),
                estimate.getCreatedAt(),
                estimate.getExpiresAt());
    }

    public FinalFareResponse toFinalFare(FinalFare fare) {
        return new FinalFareResponse(
                fare.getId(),
                fare.getRideId(),
                fare.getType(),
                fare.getVehicleType(),
                toBreakdown(fare.getBreakdown()),
                fare.getCurrency(),
                fare.getCreatedAt());
    }

    public PaymentResponse toPayment(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getRideId(),
                payment.getPassengerId(),
                payment.getDriverId(),
                payment.getType(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getMethod(),
                payment.getStatus(),
                payment.getReceiptNumber(),
                payment.getPaidAt(),
                payment.getFailureReason(),
                payment.getCreatedAt());
    }

    /**
     * Builds a receipt from a paid payment and its fare.
     *
     * <p>Callers must check {@link Payment#isPaid()} first; this method assumes it, which
     * is why the receipt endpoint 404s on an unpaid payment rather than returning a blank
     * receipt.</p>
     */
    public ReceiptResponse toReceipt(Payment payment, FinalFare fare) {
        return new ReceiptResponse(
                payment.getReceiptNumber(),
                payment.getId(),
                payment.getRideId(),
                payment.getPassengerId(),
                payment.getDriverId(),
                payment.getType(),
                toBreakdown(fare.getBreakdown()),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getMethod(),
                payment.getPaidAt());
    }
}
