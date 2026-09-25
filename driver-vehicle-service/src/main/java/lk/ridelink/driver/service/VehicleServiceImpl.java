package lk.ridelink.driver.service;

import java.util.List;
import java.util.UUID;
import lk.ridelink.driver.domain.DriverProfile;
import lk.ridelink.driver.domain.Vehicle;
import lk.ridelink.driver.dto.VehicleRequest;
import lk.ridelink.driver.dto.VehicleResponse;
import lk.ridelink.driver.exception.NotFoundException;
import lk.ridelink.driver.exception.VehicleAlreadyRegisteredException;
import lk.ridelink.driver.mapper.DriverMapper;
import lk.ridelink.driver.repository.DriverProfileRepository;
import lk.ridelink.driver.repository.VehicleRepository;
import lk.ridelink.driver.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VehicleServiceImpl implements VehicleService {

    private static final Logger log = LoggerFactory.getLogger(VehicleServiceImpl.class);

    private final VehicleRepository vehicleRepository;
    private final DriverProfileRepository driverRepository;
    private final DriverMapper driverMapper;
    private final CurrentUser currentUser;

    public VehicleServiceImpl(VehicleRepository vehicleRepository,
                              DriverProfileRepository driverRepository,
                              DriverMapper driverMapper,
                              CurrentUser currentUser) {
        this.vehicleRepository = vehicleRepository;
        this.driverRepository = driverRepository;
        this.driverMapper = driverMapper;
        this.currentUser = currentUser;
    }

    @Override
    @Transactional
    public VehicleResponse addOwnVehicle(VehicleRequest request) {
        UUID driverId = currentUser.id();
        requireDriver(driverId);

        String registration = request.registrationNumber().trim().toUpperCase();
        if (vehicleRepository.existsByRegistrationNumber(registration)) {
            throw new VehicleAlreadyRegisteredException(registration);
        }

        Vehicle vehicle = Vehicle.register(driverId, registration, request.type(), request.make(),
                request.model(), request.colour(), request.seats(), request.year());

        try {
            vehicle = vehicleRepository.saveAndFlush(vehicle);
        } catch (DataIntegrityViolationException ex) {
            // The unique index is the real guard against two drivers claiming one plate.
            throw new VehicleAlreadyRegisteredException(registration);
        }

        // First vehicle is activated automatically: requiring a separate activate call
        // for the only vehicle a driver owns would be a pointless extra step, and the
        // driver cannot go online without an active vehicle anyway.
        if (vehicleRepository.findByDriverIdAndActiveTrue(driverId).isEmpty()) {
            vehicle.activate();
            vehicleRepository.save(vehicle);
            log.info("Vehicle {} auto-activated as driver {}'s first vehicle",
                    vehicle.getRegistrationNumber(), driverId);
        }

        log.info("Driver {} registered vehicle {} ({})", driverId, registration, request.type());
        return driverMapper.toVehicle(vehicle);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VehicleResponse> listOwnVehicles() {
        return vehicleRepository.findByDriverId(currentUser.id()).stream()
                .map(driverMapper::toVehicle)
                .toList();
    }

    @Override
    @Transactional
    public VehicleResponse updateOwnVehicle(UUID vehicleId, VehicleRequest request) {
        Vehicle vehicle = requireOwnVehicle(vehicleId);

        // The registration plate is the vehicle's identity and is uniquely indexed;
        // changing it would be a new vehicle, so only the descriptive fields are editable.
        vehicle.updateDetails(request.type(), request.make(), request.model(),
                request.colour(), request.seats(), request.year());
        vehicleRepository.save(vehicle);

        log.info("Driver {} updated vehicle {}", vehicle.getDriverId(), vehicleId);
        return driverMapper.toVehicle(vehicle);
    }

    @Override
    @Transactional
    public VehicleResponse activateOwnVehicle(UUID vehicleId) {
        Vehicle vehicle = requireOwnVehicle(vehicleId);

        // Exactly one active vehicle per driver: matching filters on the active vehicle's
        // type, so two active vehicles would make the dispatched type ambiguous.
        vehicleRepository.findByDriverId(vehicle.getDriverId()).stream()
                .filter(Vehicle::isActive)
                .filter(other -> !other.getId().equals(vehicleId))
                .forEach(other -> {
                    other.deactivate();
                    vehicleRepository.save(other);
                });

        vehicle.activate();
        vehicleRepository.save(vehicle);

        log.info("Driver {} activated vehicle {} ({})",
                vehicle.getDriverId(), vehicle.getRegistrationNumber(), vehicle.getType());
        return driverMapper.toVehicle(vehicle);
    }

    private Vehicle requireOwnVehicle(UUID vehicleId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> NotFoundException.vehicle(vehicleId));

        // Ownership check belongs here rather than on the controller: @PreAuthorize can
        // say "is a driver" but cannot see whose vehicle this row is.
        if (!vehicle.belongsTo(currentUser.id())) {
            throw new AccessDeniedException("This vehicle belongs to another driver");
        }
        return vehicle;
    }

    private DriverProfile requireDriver(UUID driverId) {
        return driverRepository.findById(driverId)
                .orElseThrow(() -> NotFoundException.driver(driverId));
    }
}
