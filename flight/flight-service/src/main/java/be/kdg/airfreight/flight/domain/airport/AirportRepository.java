package be.kdg.airfreight.flight.domain.airport;

import java.util.List;

public interface AirportRepository {
    List<Airport> findAll();
}
