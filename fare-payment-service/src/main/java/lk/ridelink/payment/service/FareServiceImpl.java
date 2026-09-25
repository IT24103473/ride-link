package lk.ridelink.payment.service;

import java.time.Clock;
import java.util.UUID;
import lk.ridelink.payment.config.RideLinkProperties;
import lk.ridelink.payment.domain.FareBreakdown;
import lk.ridelink.payment.domain.FareCalculator;
import lk.ridelink.payment.domain.FareEstimate;
import lk.ridelink.payment.domain.Haversine;
import lk.ridelink.payment.domain.Location;
import lk.ridelink.payment.domain.VehicleType;
import lk.ridelink.payment.dto.FareEstimateRequest;
import lk.ridelink.payment.dto.FareEstimateResponse;
import lk.ridelink.payment.dto.TariffResponse;
import lk.ridelink.payment.exception.NotFoundException;
import lk.ridelink.payment.mapper.PaymentMapper;
import lk.ridelink.payment.repository.FareEstimateRepository;
import lk.ridelink.payment.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FareServiceImpl implements FareService {

    private static final Logger log = LoggerFactory.getLogger(FareServiceImpl.class);

    private final FareEstimateRepository estimateRepository;
    private final FareCalculator fareCalculator;
    private final PaymentMapper mapper;
    private final CurrentUser currentUser;
    private final RideLinkProperties properties;
    private final Clock clock;

    public FareServiceImpl(FareEstimateRepository estimateRepository,
                           FareCalculator fareCalculator,
                           PaymentMapper mapper,
                           CurrentUser currentUser,
                           RideLinkProperties properties,
                           Clock clock) {
        this.estimateRepository = estimateRepository;
        // Injected as the interface, so a surge or promotional calculator would be a new
        // bean rather than an edit here.
        this.fareCalculator = fareCalculator;
        this.mapper = mapper;
        this.currentUser = currentUser;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public FareEstimateResponse createEstimate(FareEstimateRequest request) {
        Location pickup = mapper.toLocation(request.pickup());
        Location destination = mapper.toLocation(request.destination());

        double straightLineKm = Haversine.distanceKm(
                pickup.lat(), pickup.lng(), destination.lat(), destination.lng());

        FareBreakdown breakdown = fareCalculator.estimate(request.vehicleType(), straightLineKm);

        FareEstimate estimate = FareEstimate.create(
                currentUser.id(), pickup, destination, request.vehicleType(), breakdown,
                properties.fare().currency(), properties.fare().estimateValidityMinutes());

        estimateRepository.save(estimate);

        log.info("Fare estimate {} for {} {}km -> {} {}",
                estimate.getId(), request.vehicleType(), breakdown.distanceKm(),
                breakdown.total(), estimate.getCurrency());

        return mapper.toEstimate(estimate);
    }

    @Override
    @Transactional(readOnly = true)
    public FareEstimateResponse getEstimate(UUID estimateId) {
        FareEstimate estimate = estimateRepository.findById(estimateId)
                .orElseThrow(() -> NotFoundException.estimate(estimateId));

        // An estimate reveals where a passenger intended to travel, so only they and an
        // admin may read it back.
        if (!currentUser.isAdmin() && !estimate.belongsTo(currentUser.id())) {
            throw new AccessDeniedException("This fare estimate belongs to another passenger");
        }

        return mapper.toEstimate(estimate);
    }

    @Override
    public TariffResponse getTariffs() {
        RideLinkProperties.Fare fare = properties.fare();

        // Built from the same configuration the calculator reads, so the published rule
        // cannot drift away from the one actually applied.
        var tariffs = java.util.Arrays.stream(VehicleType.values())
                .map(type -> {
                    RideLinkProperties.Tariff tariff = properties.tariffFor(type);
                    return new TariffResponse.VehicleTariff(type, tariff.base(), tariff.perKm(),
                            tariff.perMin(), tariff.minimum());
                })
                .toList();

        return new TariffResponse(
                fare.currency(),
                "total = max(base + perKm x roadDistanceKm + perMin x durationMin, minimum), "
                        + "where roadDistanceKm = straightLineKm x roadWindingFactor and "
                        + "durationMin = roadDistanceKm / averageSpeedKmh x 60, rounded up",
                fare.roadWindingFactor(),
                fare.averageSpeedKmh(),
                fare.estimateValidityMinutes(),
                fare.cancellationFee(),
                tariffs);
    }

    /** Exposed for the payment flow, which must know whether a quote is still honourable. */
    @Transactional(readOnly = true)
    public boolean isExpired(UUID estimateId) {
        return estimateRepository.findById(estimateId)
                .map(estimate -> estimate.isExpired(clock.instant()))
                .orElse(true);
    }
}
