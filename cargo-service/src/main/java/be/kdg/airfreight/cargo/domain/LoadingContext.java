package be.kdg.airfreight.cargo.domain;

import java.time.Instant;

// the facts one loading action is judged on
public record LoadingContext(FlightInformation flight, LoadingWindow window, Instant now) {}
