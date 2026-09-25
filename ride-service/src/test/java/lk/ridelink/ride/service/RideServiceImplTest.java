package lk.ridelink.ride.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lk.ridelink.ride.client.DriverClient;
import lk.ridelink.ride.client.FareClient;
import lk.ridelink.ride.config.RideLinkProperties;
import lk.ridelink.ride.domain.CancelledBy;
import lk.ridelink.ride.domain.Location;
import lk.ridelink.ride.domain.PaymentMethod;
import lk.ridelink.ride.domain.Ride;
import lk.ridelink.ride.domain.RideStatus;
import lk.ridelink.ride.domain.ServiceArea;
import lk.ridelink.ride.domain.VehicleType;
import lk.ridelink.ride.dto.CancelRideRequest;
import lk.ridelink.ride.dto.CompleteRideRequest;
import lk.ridelink.ride.dto.CreateRideRequest;
import lk.ridelink.ride.dto.LocationRequest;
import lk.ridelink.ride.dto.RideResponse;
import lk.ridelink.ride.exception.ActiveRideExistsException;
import lk.ridelink.ride.exception.BusinessRuleException;
import lk.ridelink.ride.exception.DownstreamUnavailableException;
import lk.ridelink.ride.exception.InvalidRideTransitionException;
import lk.ridelink.ride.exception.NoDriverAvailableException;
import lk.ridelink.ride.mapper.RideMapper;
import lk.ridelink.ride.messaging.EventPublisher;
import lk.ridelink.ride.messaging.EventTypes;
import lk.ridelink.ride.messaging.RideCancelledEvent;
import lk.ridelink.ride.messaging.RideCompletedEvent;
import lk.ridelink.ride.repository.RideRepository;
import lk.ridelink.ride.repository.RideStatusHistoryRepository;
import lk.ridelink.ride.security.CurrentUser;
import lk.ridelink.ride.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

/**
 * Orchestration: the assignment retry loop, ownership rules, event publication and how
 * downstream failures are reported.
 *
 * <p>The assignment tests carry the most weight. Retrying over candidates is the only
 * place where this service has to cope with another service telling it "no" as a normal
 * outcome, and getting it wrong would show up as passengers being told no driver exists
 * when one was simply taken a moment earlier.</p>
 */
@ExtendWith(MockitoExtension.class)
class RideServiceImplTest {

    private static final String BEARER = "Bearer passenger-token";

    @Mock
    private RideRepository rideRepository;
    @Mock
    private RideStatusHistoryRepository historyRepository;
    @Mock
    private FareClient fareClient;
    @Mock
    private DriverClient driverClient;
    @Mock
    private EventPublisher eventPublisher;
    @Mock
    private CurrentUser currentUser;

    private RideServiceImpl rideService;

    @BeforeEach
    void setUp() {
        RideLinkProperties properties = new RideLinkProperties(
                new RideLinkProperties.Security("secret"),
                new RideLinkProperties.Clients("http://account", "http://driver", "http://fare",
                        2000, 3000, "ride-service", "secret"),
                new RideLinkProperties.Assignment(3));

        rideService = new RideServiceImpl(rideRepository, historyRepository, fareClient,
                driverClient, new RideMapper(), eventPublisher, currentUser, properties);
    }

    // --- Requesting ---------------------------------------------------------

    @Test
    @DisplayName("requesting a ride stores the fare quote as a snapshot")
    void requestRide_validRequest_snapshotsTheEstimate() {
        UUID passengerId = UUID.randomUUID();
        UUID estimateId = UUID.randomUUID();
        when(currentUser.id()).thenReturn(passengerId);
        // The passenger's own token is forwarded, so the Fare service records the quote
        // against them rather than against this service.
        when(currentUser.bearerToken()).thenReturn(BEARER);
        when(rideRepository.findActiveByPassengerId(passengerId)).thenReturn(Optional.empty());
        when(fareClient.estimate(any(), any(), eq(VehicleType.CAR), eq(BEARER)))
                .thenReturn(new FareClient.FareEstimate(estimateId,
                        new FareClient.Breakdown(new BigDecimal("39.65"), 96, new BigDecimal("4595.00")),
                        "LKR"));

        RideResponse response = rideService.requestRide(validRequest());

        assertThat(response.status()).isEqualTo(RideStatus.REQUESTED);
        // Copied onto the ride, so a later tariff change cannot alter the quoted price.
        assertThat(response.fareEstimateId()).isEqualTo(estimateId);
        assertThat(response.estimatedFare()).isEqualByComparingTo("4595.00");
        assertThat(response.estimatedDistanceKm()).isEqualByComparingTo("39.65");
        assertThat(response.currency()).isEqualTo("LKR");
    }

    @Test
    @DisplayName("a passenger with an unfinished ride cannot request another")
    void requestRide_activeRideExists_throwsConflict() {
        UUID passengerId = UUID.randomUUID();
        when(currentUser.id()).thenReturn(passengerId);
        when(rideRepository.findActiveByPassengerId(passengerId))
                .thenReturn(Optional.of(rideInStatus(RideStatus.ACCEPTED)));

        assertThatThrownBy(() -> rideService.requestRide(validRequest()))
                .isInstanceOf(ActiveRideExistsException.class);

        // The fare service must not be called for a request that cannot succeed.
        verify(fareClient, never()).estimate(any(), any(), any(), any());
    }

    @Test
    @DisplayName("pickup and destination must differ")
    void requestRide_samePickupAndDestination_throws() {
        when(currentUser.id()).thenReturn(UUID.randomUUID());
        when(rideRepository.findActiveByPassengerId(any())).thenReturn(Optional.empty());

        CreateRideRequest request = new CreateRideRequest(
                new LocationRequest("Colombo Fort", 6.9344, 79.8428),
                new LocationRequest("Colombo Fort", 6.9344, 79.8428),
                VehicleType.CAR, ServiceArea.COLOMBO, PaymentMethod.CARD);

        // A zero-distance ride would travel nowhere and bill only the minimum fare.
        assertThatThrownBy(() -> rideService.requestRide(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("different places");
    }

    @Test
    @DisplayName("an unreachable fare service surfaces as 503, naming the service")
    void requestRide_fareServiceDown_throwsDownstreamUnavailable() {
        when(currentUser.id()).thenReturn(UUID.randomUUID());
        when(rideRepository.findActiveByPassengerId(any())).thenReturn(Optional.empty());
        when(fareClient.estimate(any(), any(), any(), any()))
                .thenThrow(new DownstreamUnavailableException("fare-payment", "ResourceAccessException"));

        assertThatThrownBy(() -> rideService.requestRide(validRequest()))
                .isInstanceOf(DownstreamUnavailableException.class)
                .extracting(ex -> ((DownstreamUnavailableException) ex).getServiceName())
                .isEqualTo("fare-payment");

        // A ride with no price is not a usable booking, so nothing is stored.
        verify(rideRepository, never()).save(any());
    }

    // --- Assignment ---------------------------------------------------------

    @Test
    @DisplayName("the first candidate is reserved when available")
    void assignDriver_firstCandidateAvailable_assignsThem() {
        Ride ride = rideInStatus(RideStatus.REQUESTED);
        UUID firstDriver = UUID.randomUUID();
        UUID secondDriver = UUID.randomUUID();
        arrangePassenger(ride);
        when(driverClient.findAvailable(any(), any(), any(), anyInt()))
                .thenReturn(List.of(candidate(firstDriver, 1.2), candidate(secondDriver, 3.4)));
        when(driverClient.reserve(firstDriver, ride.getId())).thenReturn(true);

        RideResponse response = rideService.assignDriver(ride.getId());

        assertThat(response.status()).isEqualTo(RideStatus.ASSIGNED);
        assertThat(response.driverId()).isEqualTo(firstDriver);
        // The loop must stop on the first success: reserving the runner-up as well would
        // take a second driver out of circulation for a ride that already has one.
        verify(driverClient, never()).reserve(eq(secondDriver), any());
    }

    @Test
    @DisplayName("when the first candidate is taken, the second is tried and wins")
    void assignDriver_firstCandidateTaken_fallsThroughToSecond() {
        Ride ride = rideInStatus(RideStatus.REQUESTED);
        UUID taken = UUID.randomUUID();
        UUID available = UUID.randomUUID();
        arrangePassenger(ride);
        when(driverClient.findAvailable(any(), any(), any(), anyInt()))
                .thenReturn(List.of(candidate(taken, 1.0), candidate(available, 2.0)));
        // A driver can be reserved by another ride between the search and this call.
        when(driverClient.reserve(taken, ride.getId())).thenReturn(false);
        when(driverClient.reserve(available, ride.getId())).thenReturn(true);

        RideResponse response = rideService.assignDriver(ride.getId());

        assertThat(response.driverId()).isEqualTo(available);
        assertThat(response.status()).isEqualTo(RideStatus.ASSIGNED);
    }

    @Test
    @DisplayName("no candidates at all gives 409 and leaves the ride REQUESTED")
    void assignDriver_noCandidates_throwsAndLeavesRideRequested() {
        Ride ride = rideInStatus(RideStatus.REQUESTED);
        arrangePassenger(ride);
        when(driverClient.findAvailable(any(), any(), any(), anyInt())).thenReturn(List.of());

        assertThatThrownBy(() -> rideService.assignDriver(ride.getId()))
                .isInstanceOf(NoDriverAvailableException.class);

        // Left REQUESTED rather than cancelled, so the passenger can simply retry.
        assertThat(ride.getStatus()).isEqualTo(RideStatus.REQUESTED);
        assertThat(ride.getDriverId()).isNull();
    }

    @Test
    @DisplayName("every candidate being taken gives 409 and leaves the ride REQUESTED")
    void assignDriver_allCandidatesTaken_throwsAndLeavesRideRequested() {
        Ride ride = rideInStatus(RideStatus.REQUESTED);
        arrangePassenger(ride);
        when(driverClient.findAvailable(any(), any(), any(), anyInt()))
                .thenReturn(List.of(candidate(UUID.randomUUID(), 1.0), candidate(UUID.randomUUID(), 2.0)));
        when(driverClient.reserve(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> rideService.assignDriver(ride.getId()))
                .isInstanceOf(NoDriverAvailableException.class);

        assertThat(ride.getStatus()).isEqualTo(RideStatus.REQUESTED);
    }

    @Test
    @DisplayName("no more than the configured number of candidates is attempted")
    void assignDriver_manyCandidates_stopsAtTheConfiguredLimit() {
        Ride ride = rideInStatus(RideStatus.REQUESTED);
        arrangePassenger(ride);
        when(driverClient.findAvailable(any(), any(), any(), anyInt()))
                .thenReturn(List.of(candidate(UUID.randomUUID(), 1.0), candidate(UUID.randomUUID(), 2.0),
                        candidate(UUID.randomUUID(), 3.0), candidate(UUID.randomUUID(), 4.0),
                        candidate(UUID.randomUUID(), 5.0)));
        when(driverClient.reserve(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> rideService.assignDriver(ride.getId()))
                .isInstanceOf(NoDriverAvailableException.class);

        // Bounded so a passenger does not wait while the whole city is tried.
        verify(driverClient, org.mockito.Mockito.times(3)).reserve(any(), any());
    }

    @Test
    @DisplayName("an unreachable driver service surfaces as 503, not as 'no driver available'")
    void assignDriver_driverServiceDown_throwsDownstreamUnavailable() {
        Ride ride = rideInStatus(RideStatus.REQUESTED);
        arrangePassenger(ride);
        when(driverClient.findAvailable(any(), any(), any(), anyInt()))
                .thenThrow(new DownstreamUnavailableException("driver-vehicle", "timeout"));

        // The distinction matters: "no driver available" says retrying might work, while
        // 503 says a component is down. Conflating them would hide an outage.
        assertThatThrownBy(() -> rideService.assignDriver(ride.getId()))
                .isInstanceOf(DownstreamUnavailableException.class);
    }

    @Test
    @DisplayName("a passenger cannot request assignment for someone else's ride")
    void assignDriver_notTheOwner_throwsAccessDenied() {
        Ride ride = rideInStatus(RideStatus.REQUESTED);
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));
        when(currentUser.isAdmin()).thenReturn(false);
        when(currentUser.id()).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> rideService.assignDriver(ride.getId()))
                .isInstanceOf(AccessDeniedException.class);

        verify(driverClient, never()).findAvailable(any(), any(), any(), anyInt());
    }

    // --- Driver actions -----------------------------------------------------

    @Test
    @DisplayName("the assigned driver can accept")
    void accept_assignedDriver_movesToAccepted() {
        Ride ride = assignedRide();
        arrangeDriver(ride);

        assertThat(rideService.accept(ride.getId()).status()).isEqualTo(RideStatus.ACCEPTED);
    }

    @Test
    @DisplayName("a different driver cannot accept someone else's assignment")
    void accept_differentDriver_throwsAccessDenied() {
        Ride ride = assignedRide();
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));
        when(currentUser.id()).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> rideService.accept(ride.getId()))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(ride.getStatus()).isEqualTo(RideStatus.ASSIGNED);
    }

    @Test
    @DisplayName("rejecting returns the ride to the pool and releases the driver")
    void reject_assignedDriver_returnsToRequestedAndReleasesDriver() {
        Ride ride = assignedRide();
        UUID driverId = ride.getDriverId();
        arrangeDriver(ride);

        RideResponse response = rideService.reject(ride.getId());

        // Back to REQUESTED, not CANCELLED: the passenger still wants a ride.
        assertThat(response.status()).isEqualTo(RideStatus.REQUESTED);
        assertThat(response.driverId()).isNull();
        verify(driverClient).release(driverId, ride.getId());
    }

    @Test
    @DisplayName("completing publishes ride.completed with the reported distance")
    void complete_withActualDistance_publishesEventCarryingIt() {
        Ride ride = inProgressRide();
        arrangeDriver(ride);

        rideService.complete(ride.getId(), new CompleteRideRequest(new BigDecimal("43.2")));

        ArgumentCaptor<RideCompletedEvent> event = ArgumentCaptor.forClass(RideCompletedEvent.class);
        verify(eventPublisher).publish(eq(EventTypes.RIDE_COMPLETED), event.capture());

        assertThat(event.getValue().rideId()).isEqualTo(ride.getId());
        assertThat(event.getValue().actualDistanceKm()).isEqualTo(43.2);
        assertThat(event.getValue().startedAt()).isNotNull();
        assertThat(event.getValue().completedAt()).isNotNull();
    }

    @Test
    @DisplayName("completing without a body publishes a null distance, not a failure")
    void complete_withoutBody_publishesNullActualDistance() {
        Ride ride = inProgressRide();
        arrangeDriver(ride);

        rideService.complete(ride.getId(), null);

        ArgumentCaptor<RideCompletedEvent> event = ArgumentCaptor.forClass(RideCompletedEvent.class);
        verify(eventPublisher).publish(eq(EventTypes.RIDE_COMPLETED), event.capture());

        // The Fare service falls back to the estimate, so a forgetful driver never blocks
        // completion.
        assertThat(event.getValue().actualDistanceKm()).isNull();
        assertThat(event.getValue().estimatedDistanceKm()).isNotNull();
    }

    @Test
    @DisplayName("a completed ride cannot be completed again")
    void complete_alreadyCompleted_throwsInvalidTransition() {
        Ride ride = inProgressRide();
        arrangeDriver(ride);
        rideService.complete(ride.getId(), null);

        assertThatThrownBy(() -> rideService.complete(ride.getId(), null))
                .isInstanceOf(InvalidRideTransitionException.class);
    }

    // --- Cancellation -------------------------------------------------------

    @Test
    @DisplayName("a passenger cancelling from ACCEPTED publishes that previous status")
    void cancel_passengerFromAccepted_publishesPreviousStatus() {
        Ride ride = acceptedRide();
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));
        when(currentUser.isAdmin()).thenReturn(false);
        when(currentUser.id()).thenReturn(ride.getPassengerId());

        rideService.cancel(ride.getId(), new CancelRideRequest("Plans changed"));

        ArgumentCaptor<RideCancelledEvent> event = ArgumentCaptor.forClass(RideCancelledEvent.class);
        verify(eventPublisher).publish(eq(EventTypes.RIDE_CANCELLED), event.capture());

        // previousStatus and cancelledBy together are what the fee rule keys on; by now
        // the ride itself is already CANCELLED, so it could not be derived downstream.
        assertThat(event.getValue().previousStatus()).isEqualTo("ACCEPTED");
        assertThat(event.getValue().cancelledBy()).isEqualTo("PASSENGER");
        assertThat(event.getValue().driverId()).isEqualTo(ride.getDriverId());
    }

    @Test
    @DisplayName("who cancelled is derived from the caller, not taken from the request")
    void cancel_byAssignedDriver_isRecordedAsDriver() {
        Ride ride = acceptedRide();
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));
        when(currentUser.isAdmin()).thenReturn(false);
        when(currentUser.id()).thenReturn(ride.getDriverId());

        RideResponse response = rideService.cancel(ride.getId(), new CancelRideRequest("Vehicle broke down"));

        // A passenger cannot claim the driver cancelled in order to dodge the fee.
        assertThat(response.cancelledBy()).isEqualTo(CancelledBy.DRIVER);
    }

    @Test
    @DisplayName("an admin cancellation is recorded as ADMIN")
    void cancel_byAdmin_isRecordedAsAdmin() {
        Ride ride = acceptedRide();
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));
        when(currentUser.isAdmin()).thenReturn(true);
        when(currentUser.id()).thenReturn(UUID.randomUUID());

        assertThat(rideService.cancel(ride.getId(), new CancelRideRequest("Support request")).cancelledBy())
                .isEqualTo(CancelledBy.ADMIN);
    }

    @Test
    @DisplayName("an in-progress ride cannot be cancelled")
    void cancel_inProgress_throwsInvalidTransition() {
        Ride ride = inProgressRide();
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));
        when(currentUser.isAdmin()).thenReturn(false);
        when(currentUser.id()).thenReturn(ride.getPassengerId());

        assertThatThrownBy(() -> rideService.cancel(ride.getId(), new CancelRideRequest("Changed mind")))
                .isInstanceOf(InvalidRideTransitionException.class);

        // No event may be published for a refused cancellation, or the Fare service would
        // charge a fee for a trip that is still running.
        verify(eventPublisher, never()).publish(anyString(), any());
    }

    @Test
    @DisplayName("a completed ride cannot be cancelled")
    void cancel_completed_throwsInvalidTransition() {
        Ride ride = inProgressRide();
        arrangeDriver(ride);
        rideService.complete(ride.getId(), null);
        when(currentUser.isAdmin()).thenReturn(false);

        assertThatThrownBy(() -> rideService.cancel(ride.getId(), new CancelRideRequest("Too late")))
                .isInstanceOf(InvalidRideTransitionException.class);
    }

    @Test
    @DisplayName("someone uninvolved cannot cancel a ride")
    void cancel_uninvolvedCaller_throwsAccessDenied() {
        Ride ride = acceptedRide();
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));
        when(currentUser.isAdmin()).thenReturn(false);
        when(currentUser.id()).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> rideService.cancel(ride.getId(), new CancelRideRequest("Nosy")))
                .isInstanceOf(AccessDeniedException.class);
    }

    // --- Reads --------------------------------------------------------------

    @Test
    @DisplayName("a stranger cannot read someone else's ride")
    void getById_uninvolvedCaller_throwsAccessDenied() {
        Ride ride = acceptedRide();
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));
        when(currentUser.isAdmin()).thenReturn(false);
        when(currentUser.id()).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> rideService.getById(ride.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("a driver's listing is scoped to their own assigned rides")
    void list_asDriver_queriesByDriverId() {
        UUID driverId = UUID.randomUUID();
        when(currentUser.role()).thenReturn(Role.DRIVER);
        when(currentUser.id()).thenReturn(driverId);
        when(rideRepository.findByDriver(eq(driverId), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        rideService.list(null, org.springframework.data.domain.PageRequest.of(0, 20));

        // Scoped in the query rather than filtered afterwards, so a driver's request can
        // never return another driver's rides even by accident.
        verify(rideRepository).findByDriver(eq(driverId), any(), any());
        verify(rideRepository, never()).findAllFiltered(any(), any());
    }

    @Test
    @DisplayName("an admin's listing spans every ride")
    void list_asAdmin_queriesAll() {
        when(currentUser.role()).thenReturn(Role.ADMIN);
        when(currentUser.id()).thenReturn(UUID.randomUUID());
        when(rideRepository.findAllFiltered(any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        rideService.list(RideStatus.COMPLETED, org.springframework.data.domain.PageRequest.of(0, 20));

        verify(rideRepository).findAllFiltered(eq(RideStatus.COMPLETED), any());
    }

    // --- Helpers ------------------------------------------------------------

    private void arrangePassenger(Ride ride) {
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));
        when(currentUser.isAdmin()).thenReturn(false);
        when(currentUser.id()).thenReturn(ride.getPassengerId());
    }

    private void arrangeDriver(Ride ride) {
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));
        when(currentUser.id()).thenReturn(ride.getDriverId());
    }

    private static CreateRideRequest validRequest() {
        return new CreateRideRequest(
                new LocationRequest("Colombo Fort", 6.9344, 79.8428),
                new LocationRequest("Negombo", 7.2083, 79.8358),
                VehicleType.CAR, ServiceArea.NEGOMBO, PaymentMethod.CARD);
    }

    private static Ride rideInStatus(RideStatus status) {
        Ride ride = Ride.request(UUID.randomUUID(),
                new Location("Colombo Fort", 6.9344, 79.8428),
                new Location("Negombo", 7.2083, 79.8358),
                VehicleType.CAR, ServiceArea.NEGOMBO, PaymentMethod.CARD);
        ride.applyFareEstimate(UUID.randomUUID(), new BigDecimal("4595.00"),
                new BigDecimal("39.65"), 96, "LKR");

        if (status != RideStatus.REQUESTED) {
            ride.assignDriver(UUID.randomUUID());
            ride.transitionTo(RideStatus.ASSIGNED);
        }
        if (status == RideStatus.ACCEPTED || status == RideStatus.IN_PROGRESS) {
            ride.transitionTo(RideStatus.ACCEPTED);
        }
        if (status == RideStatus.IN_PROGRESS) {
            ride.transitionTo(RideStatus.IN_PROGRESS);
        }
        return ride;
    }

    private static Ride assignedRide() {
        return rideInStatus(RideStatus.ASSIGNED);
    }

    private static Ride acceptedRide() {
        return rideInStatus(RideStatus.ACCEPTED);
    }

    private static Ride inProgressRide() {
        return rideInStatus(RideStatus.IN_PROGRESS);
    }

    private static DriverClient.DriverCandidate candidate(UUID driverId, double distanceKm) {
        return new DriverClient.DriverCandidate(driverId, "Kamal Silva", distanceKm,
                Instant.now(), "CAR", "WP CAB-1234", 12);
    }
}
