package be.kdg.airfreight.cargo.application.flight;

import be.kdg.airfreight.cargo.domain.flight.FlightInformation;
import be.kdg.airfreight.cargo.domain.flight.FlightRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class FlightOverviewService {
    private final FlightRepository flightRepository;

    public FlightOverviewService(FlightRepository flightRepository) {
        this.flightRepository = flightRepository;
    }

    public List<FlightInformation> findFlightsOn(
            LocalDate scheduledDepartureDate, boolean includeDeparted) {
        List<FlightInformation> flights =
                flightRepository.findByScheduledDepartureDate(scheduledDepartureDate);
        List<FlightInformation> shown = flights;
        // departed and landed flights have taken off so only planned ones stay unless asked
        if (!includeDeparted) {
            shown = flights.stream().filter(FlightInformation::planned).toList();
        }
        log.debug(
                "Day overview {}: showing {} of {} flights (includeDeparted={})",
                scheduledDepartureDate,
                shown.size(),
                flights.size(),
                includeDeparted);
        return shown;
    }
}
