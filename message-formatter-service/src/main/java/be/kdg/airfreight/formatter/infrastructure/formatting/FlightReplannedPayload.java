package be.kdg.airfreight.formatter.infrastructure.formatting;

import java.time.Instant;
import java.time.LocalDate;

// the json partner message for a replanning: which flight ended and which one replaces it
record FlightReplannedPayload(
        FlightReference oldFlight,
        FlightReference newFlight,
        Instant newScheduledDeparture,
        Instant newScheduledArrival) {
    record FlightReference(
            String flightNumber, LocalDate scheduledDepartureDate, String departureAirportCode) {}
}
