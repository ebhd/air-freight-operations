package be.kdg.airfreight.cargo.application.flight;

import static org.assertj.core.api.Assertions.assertThat;

import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import be.kdg.airfreight.cargo.domain.flight.FlightInformation;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.domain.flight.FlightRepository;
import be.kdg.airfreight.cargo.domain.flight.FlightStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FlightOverviewServiceTest {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 21);

    private final FlightInformation planned = flight("HJ221", FlightStatus.PLANNED);
    private final FlightInformation departed = flight("HJ300", FlightStatus.DEPARTED);
    private final FlightInformation landed = flight("HJ400", FlightStatus.LANDED);
    private final FlightInformation replanned = flight("HJ500", FlightStatus.REPLANNED);

    // a replanned flight is no longer planned so it is hidden like a departed one
    @Test
    void hidesDepartedLandedAndReplannedFlightsByDefault() {
        var flights = new StubFlightRepository(List.of(planned, departed, landed, replanned));

        assertThat(new FlightOverviewService(flights).findFlightsOn(DAY, false))
                .containsExactly(planned);
    }

    @Test
    void showsAllFlightsWhenDepartedAreIncluded() {
        var flights = new StubFlightRepository(List.of(planned, departed, landed, replanned));

        assertThat(new FlightOverviewService(flights).findFlightsOn(DAY, true))
                .containsExactly(planned, departed, landed, replanned);
    }

    @Test
    void readsTheSelectedDay() {
        var flights = new StubFlightRepository(List.of());
        LocalDate otherDay = LocalDate.of(2026, 10, 24);

        new FlightOverviewService(flights).findFlightsOn(otherDay, false);

        assertThat(flights.askedDate).isEqualTo(otherDay);
    }

    private static FlightInformation flight(String flightNumber, FlightStatus status) {
        return new FlightInformation(
                new FlightKey(flightNumber, DAY, new AirportCode("BRU")),
                new AirportCode("AMS"),
                status,
                Instant.parse("2026-10-21T08:00:00Z"),
                Instant.parse("2026-10-21T10:00:00Z"),
                Instant.parse("2026-10-21T08:00:00Z"));
    }

    private static class StubFlightRepository implements FlightRepository {
        private final List<FlightInformation> flightsOfTheDay;
        private LocalDate askedDate;

        private StubFlightRepository(List<FlightInformation> flightsOfTheDay) {
            this.flightsOfTheDay = flightsOfTheDay;
        }

        @Override
        public Optional<FlightInformation> findByFlightKey(FlightKey flightKey) {
            return Optional.empty();
        }

        @Override
        public List<FlightInformation> findByScheduledDepartureDate(LocalDate date) {
            askedDate = date;
            return flightsOfTheDay;
        }
    }
}
