package be.kdg.airfreight.flight.api.flight;

import java.time.Instant;
import java.util.List;

public record TimelineResponse(Instant start, Instant end, List<FlightOverviewResponse> flights) {}
