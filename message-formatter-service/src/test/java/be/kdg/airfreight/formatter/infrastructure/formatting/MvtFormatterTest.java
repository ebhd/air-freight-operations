package be.kdg.airfreight.formatter.infrastructure.formatting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import be.kdg.airfreight.formatter.application.MessageFormattingService;
import be.kdg.airfreight.formatter.domain.FlightDepartedFact;
import be.kdg.airfreight.formatter.domain.FlightEstimatesChangedFact;
import be.kdg.airfreight.formatter.domain.FlightLandedFact;
import be.kdg.airfreight.formatter.domain.MessageContent;
import be.kdg.airfreight.formatter.domain.MessagePublisher;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class MvtFormatterTest {
    private final MvtEdFormatter edFormatter = new MvtEdFormatter();
    private final MvtEaFormatter eaFormatter = new MvtEaFormatter();
    private final MvtAdFormatter adFormatter = new MvtAdFormatter();
    private final MvtAaFormatter aaFormatter = new MvtAaFormatter();

    @Test
    void edUsesTheDepartureAirportAndTheUtcDepartureTime() {
        MessageContent ed =
                edFormatter.format(
                        fact(
                                Instant.parse("2026-10-09T07:15:00Z"),
                                Instant.parse("2026-10-09T09:15:00Z")));

        assertThat(ed.type()).isEqualTo("MVT_ED");
        assertThat(ed.formatVersion()).isEqualTo(1);
        assertThat(ed.payload()).isEqualTo("MVT\nHJ221/09.SX-BAA.BRU\nED0715");
    }

    @Test
    void eaUsesTheArrivalAirportAndTheUtcArrivalTime() {
        MessageContent ea =
                eaFormatter.format(
                        fact(
                                Instant.parse("2026-10-09T07:15:00Z"),
                                Instant.parse("2026-10-09T09:15:00Z")));

        assertThat(ea.type()).isEqualTo("MVT_EA");
        assertThat(ea.formatVersion()).isEqualTo(1);
        assertThat(ea.payload()).isEqualTo("MVT\nHJ221/09.SX-BAA.AMS\nEA0915");
    }

    // line 2 keeps the scheduled day while the time is already on the next utc day
    @Test
    void timeAfterMidnightKeepsTheScheduledDay() {
        FlightEstimatesChangedFact late =
                fact(Instant.parse("2026-10-10T00:15:00Z"), Instant.parse("2026-10-10T02:05:00Z"));

        assertThat(edFormatter.format(late).payload())
                .isEqualTo("MVT\nHJ221/09.SX-BAA.BRU\nED0015");
        assertThat(eaFormatter.format(late).payload())
                .isEqualTo("MVT\nHJ221/09.SX-BAA.AMS\nEA0205");
    }

    @Test
    void changedDepartureEstimatePreparesEdAndEa() {
        var publisher = mock(MessagePublisher.class);
        var service =
                new MessageFormattingService(
                        new FlmFormatter(JsonMapper.builder().findAndAddModules().build()),
                        edFormatter,
                        eaFormatter,
                        new CpmFormatter(JsonMapper.builder().findAndAddModules().build()),
                        new MvtAdFormatter(),
                        new MvtAaFormatter(),
                        publisher);
        var sourceId = UUID.randomUUID();
        var changed =
                fact(Instant.parse("2026-10-09T07:15:00Z"), Instant.parse("2026-10-09T09:15:00Z"));

        service.prepareEstimates(sourceId, changed);

        verify(publisher)
                .publish(
                        sourceId,
                        List.of(
                                new MessageContent("MVT_ED", "MVT\nHJ221/09.SX-BAA.BRU\nED0715", 1),
                                new MessageContent(
                                        "MVT_EA", "MVT\nHJ221/09.SX-BAA.AMS\nEA0915", 1)));
    }

    @Test
    void changedArrivalEstimateOnlyPreparesEa() {
        var publisher = mock(MessagePublisher.class);
        var service =
                new MessageFormattingService(
                        new FlmFormatter(JsonMapper.builder().findAndAddModules().build()),
                        edFormatter,
                        eaFormatter,
                        new CpmFormatter(JsonMapper.builder().findAndAddModules().build()),
                        new MvtAdFormatter(),
                        new MvtAaFormatter(),
                        publisher);
        var sourceId = UUID.randomUUID();
        var changed =
                fact(Instant.parse("2026-10-09T06:45:00Z"), Instant.parse("2026-10-09T09:15:00Z"));

        service.prepareEstimates(sourceId, changed);

        verify(publisher)
                .publish(
                        sourceId,
                        List.of(
                                new MessageContent(
                                        "MVT_EA", "MVT\nHJ221/09.SX-BAA.AMS\nEA0915", 1)));
    }

    @Test
    void unchangedEstimatesPublishNothing() {
        var publisher = mock(MessagePublisher.class);
        var service =
                new MessageFormattingService(
                        new FlmFormatter(JsonMapper.builder().findAndAddModules().build()),
                        edFormatter,
                        eaFormatter,
                        new CpmFormatter(JsonMapper.builder().findAndAddModules().build()),
                        new MvtAdFormatter(),
                        new MvtAaFormatter(),
                        publisher);
        var unchanged =
                fact(Instant.parse("2026-10-09T06:45:00Z"), Instant.parse("2026-10-09T08:45:00Z"));

        service.prepareEstimates(UUID.randomUUID(), unchanged);

        verifyNoInteractions(publisher);
    }

    // msg-06: ad uses the departure airport and the actual departure time
    @Test
    void adUsesTheDepartureAirportAndTheUtcDepartureTime() {
        MessageContent ad = adFormatter.format(departed(Instant.parse("2026-10-09T07:18:00Z")));

        assertThat(ad.type()).isEqualTo("MVT_AD");
        assertThat(ad.formatVersion()).isEqualTo(1);
        assertThat(ad.payload()).isEqualTo("MVT\nHJ221/09.SXBAA.BRU\nAD0718");
    }

    @Test
    void aaUsesTheArrivalAirportAndTheUtcArrivalTime() {
        MessageContent aa = aaFormatter.format(landed(Instant.parse("2026-10-09T09:21:00Z")));

        assertThat(aa.type()).isEqualTo("MVT_AA");
        assertThat(aa.formatVersion()).isEqualTo(1);
        assertThat(aa.payload()).isEqualTo("MVT\nHJ221/09.SXBAA.AMS\nAA0921");
    }

    // a landing after midnight utc keeps day 09 in line 2, seconds are not written
    @Test
    void actualTimeAfterMidnightKeepsTheScheduledDay() {
        assertThat(aaFormatter.format(landed(Instant.parse("2026-10-10T00:15:30Z"))).payload())
                .isEqualTo("MVT\nHJ221/09.SXBAA.AMS\nAA0015");
        assertThat(adFormatter.format(departed(Instant.parse("2026-10-09T23:55:00Z"))).payload())
                .isEqualTo("MVT\nHJ221/09.SXBAA.BRU\nAD2355");
    }

    @Test
    void departurePreparesOnlyAd() {
        var publisher = mock(MessagePublisher.class);
        var service = service(publisher);
        var sourceId = UUID.randomUUID();

        service.prepareDeparture(sourceId, departed(Instant.parse("2026-10-09T07:18:00Z")));

        verify(publisher)
                .publish(
                        sourceId,
                        List.of(
                                new MessageContent(
                                        "MVT_AD", "MVT\nHJ221/09.SXBAA.BRU\nAD0718", 1)));
    }

    @Test
    void arrivalPreparesOnlyAa() {
        var publisher = mock(MessagePublisher.class);
        var service = service(publisher);
        var sourceId = UUID.randomUUID();

        service.prepareArrival(sourceId, landed(Instant.parse("2026-10-09T09:21:00Z")));

        verify(publisher)
                .publish(
                        sourceId,
                        List.of(
                                new MessageContent(
                                        "MVT_AA", "MVT\nHJ221/09.SXBAA.AMS\nAA0921", 1)));
    }

    private MessageFormattingService service(MessagePublisher publisher) {
        return new MessageFormattingService(
                new FlmFormatter(JsonMapper.builder().findAndAddModules().build()),
                edFormatter,
                eaFormatter,
                new CpmFormatter(JsonMapper.builder().findAndAddModules().build()),
                adFormatter,
                aaFormatter,
                publisher);
    }

    private static FlightDepartedFact departed(Instant actualDeparture) {
        return new FlightDepartedFact(
                "HJ221",
                LocalDate.of(2026, 10, 9),
                "BRU",
                "SXBAA",
                actualDeparture,
                Instant.parse("2026-10-09T07:19:00Z"));
    }

    private static FlightLandedFact landed(Instant actualArrival) {
        return new FlightLandedFact(
                "HJ221",
                LocalDate.of(2026, 10, 9),
                "AMS",
                "SXBAA",
                actualArrival,
                Instant.parse("2026-10-09T09:22:00Z"));
    }

    private static FlightEstimatesChangedFact fact(
            Instant estimatedDeparture, Instant estimatedArrival) {
        return new FlightEstimatesChangedFact(
                "HJ221",
                LocalDate.of(2026, 10, 9),
                "BRU",
                "AMS",
                "SX-BAA",
                Instant.parse("2026-10-09T06:45:00Z"),
                Instant.parse("2026-10-09T08:45:00Z"),
                estimatedDeparture,
                estimatedArrival,
                Instant.parse("2026-10-09T05:00:00Z"));
    }
}
