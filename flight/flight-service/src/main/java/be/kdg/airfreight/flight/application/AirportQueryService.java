package be.kdg.airfreight.flight.application;

import be.kdg.airfreight.flight.domain.airport.Airport;
import be.kdg.airfreight.flight.domain.airport.AirportRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AirportQueryService {
    private final AirportRepository airports;

    public AirportQueryService(AirportRepository airports) {
        this.airports = airports;
    }

    @Transactional(readOnly = true)
    public List<Airport> findAll() {
        return airports.findAll();
    }
}
