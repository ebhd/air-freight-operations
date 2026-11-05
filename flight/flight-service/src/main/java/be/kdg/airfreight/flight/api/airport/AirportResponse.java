package be.kdg.airfreight.flight.api.airport;

import be.kdg.airfreight.flight.domain.airport.Airport;

public record AirportResponse(String code, String name) {
    static AirportResponse from(Airport airport) {
        return new AirportResponse(airport.code(), airport.name());
    }
}
