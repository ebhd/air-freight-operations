package be.kdg.airfreight.flight.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.kdg.airfreight.flight.application.replanning.FlightReplanningService;
import be.kdg.airfreight.flight.application.replanning.ReplanFlightCommand;
import be.kdg.airfreight.flight.domain.NotFoundException;
import be.kdg.airfreight.flight.domain.delayreason.DelayReason;
import be.kdg.airfreight.flight.domain.delayreason.DelayReasonRepository;
import be.kdg.airfreight.flight.domain.events.FlightReplanned;
import be.kdg.airfreight.flight.domain.flight.Delay;
import be.kdg.airfreight.flight.domain.flight.DelayType;
import be.kdg.airfreight.flight.domain.flight.Flight;
import be.kdg.airfreight.flight.domain.flight.FlightKey;
import be.kdg.airfreight.flight.domain.flight.FlightStatus;
import be.kdg.airfreight.flight.domain.flight.InvalidDelayException;
import be.kdg.airfreight.flight.domain.flight.replanning.Replanning;
import be.kdg.airfreight.flight.domain.flight.replanning.ReplanningRefusal;
import be.kdg.airfreight.flight.domain.flight.replanning.ReplanningRefusedException;
import be.kdg.airfreight.flight.support.InMemoryFlights;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FlightReplanningServiceTest {
    private static final FlightKey KEY = new FlightKey("HJ500", LocalDate.of(2026, 11, 10), "BRU");
    private static final LocalDate NEW_DATE = LocalDate.of(2026, 11, 11);
    private static final Instant STD = Instant.parse("2026-11-10T20:00:00Z");
    private static final Instant STA = Instant.parse("2026-11-10T22:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-10T14:03:12Z");

    private final InMemoryFlights flights = new InMemoryFlights();
    private final List<Object> events = new ArrayList<>();
    private final FlightReplanningService service =
            new FlightReplanningService(
                    flights, new OneReason(), events::add, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        flights.save(
                Flight.restore(
                        KEY,
                        "AMS",
                        "SXBAA",
                        STD,
                        STA,
                        FlightStatus.PLANNED,
                        List.of(Delay.restore(UUID.randomUUID(), 400, "71", "Fog")),
                        List.of(Delay.restore(UUID.randomUUID(), 200, "71", "Holding"))));
    }

    @Test
    void replanSavesTheOldFlightAndPublishesTheEvent() {
        Flight replanned = service.replan(departureRequest(140, "71"));

        assertThat(replanned.status()).isEqualTo(FlightStatus.REPLANNED);
        assertThat(flights.findById(KEY).orElseThrow().status()).isEqualTo(FlightStatus.REPLANNED);
        Replanning replanning = replanned.replanning().orElseThrow();
        assertThat(replanning.newFlightNumber()).isEqualTo("HJ500A");
        assertThat(replanning.replannedAt()).isEqualTo(NOW);
        assertThat(events)
                .singleElement()
                .isEqualTo(new FlightReplanned(KEY, "AMS", "SXBAA", STD, STA, replanning, NOW));
    }

    @Test
    void takenLetterGivesTheNextOne() {
        flights.save(otherFlight("HJ500A", NEW_DATE));

        Flight replanned = service.replan(departureRequest(140, "71"));

        assertThat(replanned.replanning().orElseThrow().newFlightNumber()).isEqualTo("HJ500B");
    }

    // the key of the same number on another date is not in the way
    @Test
    void sameLetterOnAnotherDateIsNotInTheWay() {
        flights.save(otherFlight("HJ500A", NEW_DATE.plusDays(1)));

        Flight replanned = service.replan(departureRequest(140, "71"));

        assertThat(replanned.replanning().orElseThrow().newFlightNumber()).isEqualTo("HJ500A");
    }

    @Test
    void everyLetterTakenIsRefusedAndNothingIsSaved() {
        for (char letter = 'A'; letter <= 'Z'; letter++) {
            flights.save(otherFlight("HJ500" + letter, NEW_DATE));
        }

        assertThatThrownBy(() -> service.replan(departureRequest(140, "71")))
                .isInstanceOfSatisfying(
                        ReplanningRefusedException.class,
                        refused ->
                                assertThat(refused.refusal())
                                        .isEqualTo(ReplanningRefusal.NO_FREE_FLIGHT_NUMBER));
        assertThat(flights.findById(KEY).orElseThrow().status()).isEqualTo(FlightStatus.PLANNED);
        assertThat(events).isEmpty();
    }

    @Test
    void unknownReasonOfAnAddIsInvalidInput() {
        assertThatThrownBy(() -> service.replan(departureRequest(140, "99")))
                .isInstanceOf(InvalidDelayException.class)
                .hasMessage("Unknown delay reason code 99");
        assertThat(events).isEmpty();
    }

    @Test
    void unknownFlightIsNotFound() {
        FlightKey unknown = new FlightKey("HJ999", LocalDate.of(2026, 11, 10), "BRU");

        assertThatThrownBy(
                        () ->
                                service.replan(
                                        new ReplanFlightCommand(
                                                unknown,
                                                DelayType.DEPARTURE,
                                                null,
                                                140,
                                                "71",
                                                "Leak")))
                .isInstanceOf(NotFoundException.class);
    }

    // the dialog and the result must never differ
    @Test
    void proposalEqualsTheReplanThatFollows() {
        Flight flight = flights.findById(KEY).orElseThrow();
        ReplanFlightCommand request = departureRequest(140, "71");

        Replanning proposal = service.propose(flight, request);
        assertThat(flight.status()).isEqualTo(FlightStatus.PLANNED);
        Replanning replanning = service.replan(request).replanning().orElseThrow();

        assertThat(proposal).isEqualTo(replanning);
    }

    private static ReplanFlightCommand departureRequest(int minutes, String reasonCode) {
        return new ReplanFlightCommand(
                KEY, DelayType.DEPARTURE, null, minutes, reasonCode, "Hydraulic leak");
    }

    private static Flight otherFlight(String number, LocalDate date) {
        Instant departure = date.atTime(9, 0).toInstant(ZoneOffset.UTC);
        return Flight.create(
                new FlightKey(number, date, "BRU"),
                "AMS",
                "SXBAB",
                departure,
                departure.plusSeconds(7200));
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
