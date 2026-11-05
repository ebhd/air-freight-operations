package be.kdg.airfreight.flight.domain.flight;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public record FlightSchedule(
        LocalDate startDate,
        LocalDate endDate,
        Set<DayOfWeek> operatingDays,
        LocalTime departureTime,
        LocalTime arrivalTime,
        String flightNumber,
        String departureAirportCode,
        String arrivalAirportCode,
        String aircraftRegistration) {
    public FlightSchedule {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw new InvalidScheduleException("schedule dates must be present and ordered");
        }
        if (operatingDays == null || operatingDays.isEmpty()) {
            throw new InvalidScheduleException("at least one operating day is required");
        }
        operatingDays = Set.copyOf(operatingDays);
        if (departureTime == null || arrivalTime == null) {
            throw new InvalidScheduleException("departure and arrival times are required");
        }
        flightNumber = FlightKey.requireFlightNumber(flightNumber);
        departureAirportCode =
                FlightKey.requireAirportCode(departureAirportCode, "departure airport code");
        arrivalAirportCode =
                FlightKey.requireAirportCode(arrivalAirportCode, "arrival airport code");
        aircraftRegistration = requireText(aircraftRegistration, "aircraft registration");
    }

    public List<Flight> expand() {
        List<Flight> flights = new ArrayList<>();
        LocalDate departureDate = startDate;

        while (!departureDate.isAfter(endDate)) {
            if (operatingDays.contains(departureDate.getDayOfWeek())) {
                LocalDate arrivalDate =
                        arrivalTime.isAfter(departureTime)
                                ? departureDate
                                : departureDate.plusDays(1);

                Instant scheduledDeparture =
                        departureDate.atTime(departureTime).toInstant(ZoneOffset.UTC);
                Instant scheduledArrival =
                        arrivalDate.atTime(arrivalTime).toInstant(ZoneOffset.UTC);

                FlightKey key = new FlightKey(flightNumber, departureDate, departureAirportCode);
                Flight flight =
                        Flight.create(
                                key,
                                arrivalAirportCode,
                                aircraftRegistration,
                                scheduledDeparture,
                                scheduledArrival);

                flights.add(flight);
            }

            departureDate = departureDate.plusDays(1);
        }

        return List.copyOf(flights);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new InvalidScheduleException(name + " must not be blank");
        }
        return value.trim();
    }
}
