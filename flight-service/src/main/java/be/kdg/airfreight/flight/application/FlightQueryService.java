package be.kdg.airfreight.flight.application;

import be.kdg.airfreight.flight.domain.Flight;
import be.kdg.airfreight.flight.domain.FlightKey;
import be.kdg.airfreight.flight.domain.FlightRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FlightQueryService {
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
}
