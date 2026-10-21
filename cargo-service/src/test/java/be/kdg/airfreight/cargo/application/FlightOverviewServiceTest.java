package be.kdg.airfreight.cargo.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.kdg.airfreight.cargo.domain.AirportCode;
import be.kdg.airfreight.cargo.domain.FlightInformation;
import be.kdg.airfreight.cargo.domain.FlightKey;
import be.kdg.airfreight.cargo.domain.FlightLookupUnavailableException;
import be.kdg.airfreight.cargo.domain.FlightRepository;
import be.kdg.airfreight.cargo.domain.FlightStatus;
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

    @Test
    void hidesDepartedAndLandedFlightsByDefault() {
        var flights = new StubFlightRepository(List.of(planned, departed, landed));

        assertThat(new FlightOverviewService(flights).findFlightsOn(DAY, false))
                .containsExactly(planned);
    }

    @Test
    void showsAllFlightsWhenDepartedAreIncluded() {
        var flights = new StubFlightRepository(List.of(planned, departed, landed));

        assertThat(new FlightOverviewService(flights).findFlightsOn(DAY, true))
                .containsExactly(planned, departed, landed);
    }

    @Test
    void asksFlightForTheSelectedDay() {
        var flights = new StubFlightRepository(List.of());
        LocalDate otherDay = LocalDate.of(2026, 10, 24);

        new FlightOverviewService(flights).findFlightsOn(otherDay, false);

        assertThat(flights.askedDate).isEqualTo(otherDay);
    }

    @Test
    void flightOutageIsNotHidden() {
        FlightRepository flights =
                new StubFlightRepository(List.of()) {
                    @Override
                    public List<FlightInformation> findByScheduledDepartureDate(LocalDate date) {
                        throw new FlightLookupUnavailableException(date, null);
                    }
                };

        assertThatThrownBy(() -> new FlightOverviewService(flights).findFlightsOn(DAY, false))
                .isInstanceOf(FlightLookupUnavailableException.class);
    }

    private static FlightInformation flight(String flightNumber, FlightStatus status) {
        return new FlightInformation(
                new FlightKey(flightNumber, DAY, new AirportCode("BRU")),
                new AirportCode("AMS"),
                status,
                Instant.parse("2026-10-21T08:00:00Z"),
                Instant.parse("2026-10-21T10:00:00Z"));
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
