package be.kdg.airfreight.flight.application;

import be.kdg.airfreight.flight.domain.flight.Flight;
import be.kdg.airfreight.flight.domain.flight.FlightKey;
import be.kdg.airfreight.flight.domain.flight.FlightRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FlightQueryService {
    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");
    private final FlightRepository flightRepository;

    public FlightQueryService(FlightRepository flightRepository) {
        this.flightRepository = flightRepository;
    }

    @Transactional(readOnly = true)
    public Optional<Flight> findFlight(FlightKey key) {
        return flightRepository.findById(key);
    }

    @Transactional(readOnly = true)
    public List<Flight> findFlightsOn(LocalDate scheduledDepartureDate) {
        return flightRepository.findByScheduledDepartureDate(scheduledDepartureDate);
    }

    @Transactional(readOnly = true)
    public Timeline findTimelineOn(LocalDate date) {
        Instant start = date.atStartOfDay(BRUSSELS).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(BRUSSELS).toInstant();
        var visibleFlights = new ArrayList<Flight>();
        // A delay can move an older flight into this day. Use the domain estimates to check it.
        for (Flight flight : flightRepository.findScheduledBefore(end)) {
            boolean scheduledOnDay =
                    flight.scheduledDeparture().isBefore(end)
                            && flight.scheduledArrival().isAfter(start);
            boolean expectedOnDay =
                    flight.estimatedDeparture().isBefore(end)
                            && flight.estimatedArrival().isAfter(start);
            if (scheduledOnDay || expectedOnDay) {
                visibleFlights.add(flight);
            }
        }
        return new Timeline(start, end, visibleFlights);
    }

    public record Timeline(Instant start, Instant end, List<Flight> flights) {}
}
