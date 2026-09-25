package lk.ridelink.ride.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A place name with simulated coordinates")
public record LocationResponse(String name, Double lat, Double lng) {
}
