package be.kdg.airfreight.simulator.replanning;

import java.time.Instant;
import java.time.LocalDate;

// the fields of flights flight.replanned json version 1 that the simulator uses
// jackson ignores the other fields
public record FlightReplannedMessage(
        String messageId,
        Integer version,
        String flightNumber,
        LocalDate scheduledDepartureDate,
        String departureAirportCode,
        String arrivalAirportCode,
        String aircraftRegistration,
        String newFlightNumber,
        Instant newScheduledDeparture,
        Instant newScheduledArrival) {
    static final int VERSION = 1;
}
