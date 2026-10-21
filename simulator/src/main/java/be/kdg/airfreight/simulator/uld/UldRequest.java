package be.kdg.airfreight.simulator.uld;

import java.time.LocalDate;

// one uld record as the tester sends it with the full utc scheduled departure date
public record UldRequest(
        // optional fixed id to replay the same message
        String messageId,
        String flightNumber,
        LocalDate scheduledDepartureDate,
        String departureAirportCode,
        String uldCode,
        UldWeight weight,
        String destination) {}
