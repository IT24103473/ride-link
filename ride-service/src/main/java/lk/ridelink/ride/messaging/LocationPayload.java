package lk.ridelink.ride.messaging;

/** A place, as carried on an event. */
public record LocationPayload(String name, Double lat, Double lng) {
}
