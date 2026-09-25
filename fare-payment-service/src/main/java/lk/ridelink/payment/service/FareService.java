package lk.ridelink.payment.service;

import java.util.UUID;
import lk.ridelink.payment.dto.FareEstimateRequest;
import lk.ridelink.payment.dto.FareEstimateResponse;
import lk.ridelink.payment.dto.TariffResponse;

/** Fare quotes and the published pricing rule. */
public interface FareService {

    /** Quotes a price and stores it with an expiry. */
    FareEstimateResponse createEstimate(FareEstimateRequest request);

    /** Readable by the passenger who requested it, or an admin. */
    FareEstimateResponse getEstimate(UUID estimateId);

    /** The formula and rates, published so clients can see how a price was reached. */
    TariffResponse getTariffs();
}
