package be.kdg.airfreight.flight.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.kdg.airfreight.flight.application.command.AddDelayCommand;
import be.kdg.airfreight.flight.application.command.UpdateDelayCommand;
import be.kdg.airfreight.flight.domain.NotFoundException;
import be.kdg.airfreight.flight.domain.delayreason.DelayReason;
import be.kdg.airfreight.flight.domain.delayreason.DelayReasonRepository;
import be.kdg.airfreight.flight.domain.events.FlightEstimatesChanged;
import be.kdg.airfreight.flight.domain.flight.Delay;
import be.kdg.airfreight.flight.domain.flight.Flight;
import be.kdg.airfreight.flight.domain.flight.FlightKey;
import be.kdg.airfreight.flight.domain.flight.FlightRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FlightDelayServiceTest {
    private static final FlightKey KEY = new FlightKey("HJ221", LocalDate.of(2026, 10, 19), "BRU");
    private static final Instant STD = Instant.parse("2026-10-19T08:00:00Z");
    private static final Instant STA = Instant.parse("2026-10-19T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-19T06:30:00Z");

    private final InMemoryFlights flights = new InMemoryFlights();
    private final List<Object> events = new ArrayList<>();
    private final FlightDelayService service =
            new FlightDelayService(
                    flights, new OneReason(), events::add, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        flights.save(Flight.create(KEY, "AMS", "SXBAA", STD, STA));
    }

    @Test
    void departureDelayPublishesBothOldAndNewEstimates() {
        service.addDepartureDelay(command(20));

        assertThat(events)
                .singleElement()
                .isEqualTo(
                        new FlightEstimatesChanged(
                                KEY,
                                "AMS",
                                "SXBAA",
                                STD,
                                STA,
                                STD.plus(Duration.ofMinutes(20)),
                                STA.plus(Duration.ofMinutes(20)),
                                NOW));
    }

    @Test
    void arrivalDelayPublishesAnUnchangedDepartureAndALaterArrival() {
        service.addArrivalDelay(command(15));

        FlightEstimatesChanged event = (FlightEstimatesChanged) events.getFirst();
        assertThat(event.previousEstimatedDeparture()).isEqualTo(STD);
        assertThat(event.estimatedDeparture()).isEqualTo(STD);
        assertThat(event.previousEstimatedArrival()).isEqualTo(STA);
        assertThat(event.estimatedArrival()).isEqualTo(STA.plus(Duration.ofMinutes(15)));
    }

    @Test
    void descriptionOnlyEditPublishesNothing() {
        UUID delayId = service.addDepartureDelay(command(20)).departureDelays().getFirst().id();
        events.clear();

        service.updateDepartureDelay(new UpdateDelayCommand(KEY, delayId, 20, "Fog cleared later"));

        assertThat(events).isEmpty();
        assertThat(flights.findById(KEY).orElseThrow().departureDelays())
                .extracting(Delay::description)
                .containsExactly("Fog cleared later");
    }

    @Test
    void removingTheLastDelayPublishesTheReturnToSchedule() {
        UUID delayId = service.addDepartureDelay(command(20)).departureDelays().getFirst().id();
        events.clear();

        service.removeDepartureDelay(KEY, delayId);

        FlightEstimatesChanged event = (FlightEstimatesChanged) events.getFirst();
        assertThat(event.previousEstimatedDeparture()).isEqualTo(STD.plus(Duration.ofMinutes(20)));
        assertThat(event.estimatedDeparture()).isEqualTo(STD);
        assertThat(event.estimatedArrival()).isEqualTo(STA);
    }

    @Test
    void unknownFlightIsNotFoundAndPublishesNothing() {
        FlightKey unknown = new FlightKey("HJ999", LocalDate.of(2026, 10, 19), "BRU");

        assertThatThrownBy(
                        () ->
                                service.addDepartureDelay(
                                        new AddDelayCommand(unknown, 20, "71", "Fog at departure")))
                .isInstanceOf(NotFoundException.class);
        assertThat(events).isEmpty();
    }

    private static AddDelayCommand command(int minutes) {
        return new AddDelayCommand(KEY, minutes, "71", "Fog at departure");
    }

    private static final class InMemoryFlights implements FlightRepository {
        private final Map<FlightKey, Flight> flights = new HashMap<>();

        @Override
        public Optional<Flight> findById(FlightKey key) {
            return Optional.ofNullable(flights.get(key));
        }

        @Override
        public Optional<Flight> findByIdForUpdate(FlightKey key) {
            return findById(key);
        }

        @Override
        public List<Flight> findByScheduledDepartureDate(LocalDate date) {
            return List.of();
        }

        @Override
        public List<Flight> findScheduledBefore(Instant end) {
            return flights.values().stream()
                    .filter(flight -> flight.scheduledDeparture().isBefore(end))
                    .sorted(Comparator.comparing(Flight::scheduledDeparture))
                    .toList();
        }

        @Override
        public Flight save(Flight flight) {
            flights.put(flight.key(), flight);
            return flight;
        }
    }

    private static final class OneReason implements DelayReasonRepository {
        @Override
        public Optional<DelayReason> findByCode(String code) {
            return code.equals("71")
                    ? Optional.of(new DelayReason("71", "Weather at departure station"))
                    : Optional.empty();
        }

        @Override
        public List<DelayReason> findAll() {
            return List.of(new DelayReason("71", "Weather at departure station"));
        }
    }
}
