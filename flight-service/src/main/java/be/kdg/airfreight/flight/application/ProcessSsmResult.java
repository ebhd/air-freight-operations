package be.kdg.airfreight.flight.application;

import be.kdg.airfreight.flight.domain.Flight;
import java.util.List;

public record ProcessSsmResult(
        String messageId, List<Flight> createdFlights, List<String> duplicateFlightKeys) {
    public ProcessSsmResult {
        createdFlights = List.copyOf(createdFlights);
        duplicateFlightKeys = List.copyOf(duplicateFlightKeys);
    }
}
