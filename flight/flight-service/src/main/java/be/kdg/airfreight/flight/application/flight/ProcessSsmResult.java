package be.kdg.airfreight.flight.application.flight;

import be.kdg.airfreight.flight.domain.flight.Flight;
import java.util.List;

public record ProcessSsmResult(
        String messageId, List<Flight> createdFlights, List<String> duplicateFlightKeys) {
    public ProcessSsmResult {
        createdFlights = List.copyOf(createdFlights);
        duplicateFlightKeys = List.copyOf(duplicateFlightKeys);
    }
}
