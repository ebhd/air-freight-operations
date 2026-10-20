package be.kdg.airfreight.cargo.application;

import be.kdg.airfreight.cargo.domain.FlightInformation;
import be.kdg.airfreight.cargo.domain.FlightRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class FlightOverviewService {
    private final FlightRepository flightRepository;

    public FlightOverviewService(FlightRepository flightRepository) {
        this.flightRepository = flightRepository;
    }

    public List<FlightInformation> findFlightsOn(LocalDate scheduledDepartureDate) {
        return flightRepository.findByScheduledDepartureDate(scheduledDepartureDate);
    }
}
