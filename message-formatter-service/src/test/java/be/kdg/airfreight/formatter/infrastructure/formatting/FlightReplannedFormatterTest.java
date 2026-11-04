package be.kdg.airfreight.formatter.infrastructure.formatting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import be.kdg.airfreight.formatter.application.MessageFormattingService;
import be.kdg.airfreight.formatter.domain.FlightReplacedFact;
import be.kdg.airfreight.formatter.domain.MessageContent;
import be.kdg.airfreight.formatter.domain.MessagePublisher;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class FlightReplannedFormatterTest {
    private static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();
    private final FlightReplannedFormatter formatter = new FlightReplannedFormatter(JSON);

    // the plan example: HJ500 of 10 nov replaced by HJ500A departing 11 nov 05:00Z
    @Test
    void messageNamesBothFlightsAndTheNewSchedule() {
        MessageContent content = formatter.format(fact());

        assertThat(content.type()).isEqualTo("FLIGHT_REPLANNED");
        assertThat(content.formatVersion()).isEqualTo(1);
        JsonNode payload = JSON.readTree(content.payload());
        assertThat(payload.get("oldFlight").get("flightNumber").asString()).isEqualTo("HJ500");
        assertThat(payload.get("oldFlight").get("scheduledDepartureDate").asString())
                .isEqualTo("2026-11-10");
        assertThat(payload.get("oldFlight").get("departureAirportCode").asString())
                .isEqualTo("BRU");
        assertThat(payload.get("newFlight").get("flightNumber").asString()).isEqualTo("HJ500A");
        assertThat(payload.get("newFlight").get("scheduledDepartureDate").asString())
                .isEqualTo("2026-11-11");
        assertThat(payload.get("newFlight").get("departureAirportCode").asString())
                .isEqualTo("BRU");
        assertThat(payload.get("newScheduledDeparture").asString())
                .isEqualTo("2026-11-11T05:00:00Z");
        assertThat(payload.get("newScheduledArrival").asString()).isEqualTo("2026-11-11T10:20:00Z");
    }

    @Test
    void serviceHandsTheMessageToThePublisherWithTheFlightMessageId() {
        var publisher = mock(MessagePublisher.class);
        var service =
                new MessageFormattingService(
                        new FlmFormatter(JSON),
                        new MvtEdFormatter(),
                        new MvtEaFormatter(),
                        new CpmFormatter(JSON),
                        new MvtAdFormatter(),
                        new MvtAaFormatter(),
                        formatter,
                        publisher);
        var sourceId = UUID.randomUUID();

        service.prepareFlightReplanned(sourceId, fact());

        verify(publisher).publish(sourceId, List.of(formatter.format(fact())));
    }

    @Test
    void factRefusesAnArrivalBeforeTheDepartureAndABadAirport() {
        assertThatThrownBy(
                        () ->
                                new FlightReplacedFact(
                                        "HJ500",
                                        LocalDate.of(2026, 11, 10),
                                        "BRU",
                                        "HJ500A",
                                        LocalDate.of(2026, 11, 11),
                                        "BRU",
                                        Instant.parse("2026-11-11T05:00:00Z"),
                                        Instant.parse("2026-11-11T05:00:00Z"),
                                        Instant.parse("2026-11-10T14:03:15Z")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("new scheduled arrival must be after departure");
        assertThatThrownBy(
                        () ->
                                new FlightReplacedFact(
                                        "HJ500",
                                        LocalDate.of(2026, 11, 10),
                                        "bru",
                                        "HJ500A",
                                        LocalDate.of(2026, 11, 11),
                                        "BRU",
                                        Instant.parse("2026-11-11T05:00:00Z"),
                                        Instant.parse("2026-11-11T10:20:00Z"),
                                        Instant.parse("2026-11-10T14:03:15Z")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("airport code must be a three-letter IATA code");
    }

    private static FlightReplacedFact fact() {
        return new FlightReplacedFact(
                "HJ500",
                LocalDate.of(2026, 11, 10),
                "BRU",
                "HJ500A",
                LocalDate.of(2026, 11, 11),
                "BRU",
                Instant.parse("2026-11-11T05:00:00Z"),
                Instant.parse("2026-11-11T10:20:00Z"),
                Instant.parse("2026-11-10T14:03:15Z"));
    }
}
