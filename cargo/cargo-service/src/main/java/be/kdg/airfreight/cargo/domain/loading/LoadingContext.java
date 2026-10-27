package be.kdg.airfreight.cargo.domain.loading;

import be.kdg.airfreight.cargo.domain.flight.FlightInformation;
import java.time.Instant;

// the facts one loading action is judged on
public record LoadingContext(FlightInformation flight, LoadingWindow window, Instant now) {}
