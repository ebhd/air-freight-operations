package be.kdg.airfreight.flight.support;

import be.kdg.airfreight.flight.domain.flight.Flight;
import be.kdg.airfreight.flight.domain.flight.FlightKey;
import be.kdg.airfreight.flight.domain.flight.FlightRepository;
import be.kdg.airfreight.flight.domain.flight.FlightStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Shared test storage. The counters record calls, not real database locks or commits.
public final class InMemoryFlights implements FlightRepository {
    public final Map<FlightKey, Flight> flights;
    public int lockedLookups;
    public int saves;

    public InMemoryFlights() {
        this(new LinkedHashMap<>());
    }

    public InMemoryFlights(Map<FlightKey, Flight> flights) {
        this.flights = flights;
    }

    @Override
    public Optional<Flight> findById(FlightKey key) {
        return Optional.ofNullable(flights.get(key));
    }

    @Override
    public Optional<Flight> findByIdForUpdate(FlightKey key) {
        lockedLookups++;
        return findById(key);
    }

    @Override
    public List<Flight> findByScheduledDepartureDate(LocalDate date) {
        return flights.values().stream()
                .filter(flight -> flight.key().scheduledDepartureDate().equals(date))
                .sorted(Comparator.comparing(Flight::scheduledDeparture))
                .toList();
    }

    @Override
    public List<Flight> findScheduledBefore(Instant end) {
        return flights.values().stream()
                .filter(flight -> flight.scheduledDeparture().isBefore(end))
                .sorted(Comparator.comparing(Flight::scheduledDeparture))
                .toList();
    }

    @Override
    public List<FlightKey> findKeysUpTo(LocalDate lastDate) {
        return flights.keySet().stream()
                .filter(key -> !key.scheduledDepartureDate().isAfter(lastDate))
                .toList();
    }

    @Override
    public Flight save(Flight flight) {
        saves++;
        flights.put(flight.key(), flight);
        return flight;
    }

    @Override
    public void delete(FlightKey key) {
        flights.remove(key);
    }

    @Override
    public Optional<Flight> findReplacedBy(FlightKey newKey, Instant newScheduledDeparture) {
        return flights.values().stream()
                .filter(flight -> flight.status() == FlightStatus.REPLANNED)
                .filter(flight -> !flight.replacementCreated())
                .filter(
                        flight ->
                                flight.replanning()
                                        .orElseThrow()
                                        .newKey(flight.key().departureAirportCode())
                                        .equals(newKey))
                .filter(
                        flight ->
                                flight.replanning()
                                        .orElseThrow()
                                        .newScheduledDeparture()
                                        .equals(newScheduledDeparture))
                .findFirst();
    }
}
