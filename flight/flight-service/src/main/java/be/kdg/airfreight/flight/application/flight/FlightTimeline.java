package be.kdg.airfreight.flight.application.flight;

import be.kdg.airfreight.flight.domain.flight.Flight;
import java.time.Instant;
import java.util.List;

public record FlightTimeline(Instant start, Instant end, List<Flight> flights) {}
