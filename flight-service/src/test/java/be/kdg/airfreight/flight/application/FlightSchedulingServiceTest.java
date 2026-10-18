package be.kdg.airfreight.flight.application;

import static org.assertj.core.api.Assertions.assertThat;

import be.kdg.airfreight.flight.domain.Flight;
import be.kdg.airfreight.flight.domain.FlightKey;
import be.kdg.airfreight.flight.domain.FlightSchedule;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FlightSchedulingServiceTest {
    @Test
    void createsMatchingFlightsAndContinuesAfterExistingFlight() {
        var repository = new InMemoryFlights();
        var schedule = schedule(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 14));
        repository.save(schedule.expand().getFirst());
        var service = new FlightSchedulingService(repository);

        var result = service.createFlights("message-1", schedule);

        assertThat(result.createdFlights()).hasSize(1);
        assertThat(result.duplicateFlightKeys()).hasSize(1);
        assertThat(repository.flights).hasSize(2);
    }

    @Test
    void redeliveredMessageHasNoSecondEffect() {
        var repository = new InMemoryFlights();
        var service = new FlightSchedulingService(repository);
        var schedule = schedule(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 12));

        var first = service.createFlights("message-1", schedule);
        var second = service.createFlights("message-1", schedule);

        assertThat(first.createdFlights()).hasSize(1);
        assertThat(second.createdFlights()).isEmpty();
        assertThat(second.duplicateFlightKeys())
                .containsExactly(first.createdFlights().getFirst().key().toString());
        assertThat(repository.flights).hasSize(1);
        assertThat(repository.flights.get(first.createdFlights().getFirst().key()))
                .isSameAs(first.createdFlights().getFirst());
    }

    private static FlightSchedule schedule(LocalDate start, LocalDate end) {
        return new FlightSchedule(
                start,
                end,
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                LocalTime.of(8, 0),
                LocalTime.of(10, 0),
                "HJ221",
                "BRU",
                "AMS",
                "SXBAA");
    }

    private static final class InMemoryFlights
            implements be.kdg.airfreight.flight.domain.FlightRepository {
        private final Map<FlightKey, Flight> flights = new HashMap<>();

        @Override
        public Optional<Flight> findById(FlightKey key) {
            return Optional.ofNullable(flights.get(key));
        }

        @Override
        public Flight save(Flight flight) {
            flights.put(flight.key(), flight);
            return flight;
        }
    }
}
