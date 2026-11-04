package be.kdg.airfreight.formatter.infrastructure.formatting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import be.kdg.airfreight.formatter.application.MessageFormattingService;
import be.kdg.airfreight.formatter.domain.LoadFinalizedFact;
import be.kdg.airfreight.formatter.domain.MessageContent;
import be.kdg.airfreight.formatter.domain.MessagePublisher;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class CpmFormatterTest {
    private static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();
    private final CpmFormatter formatter = new CpmFormatter(JSON);

    @Test
    void cpmHasTheFlightAndEveryUldWithPositionWeightUnitAndDestination() {
        MessageContent cpm =
                formatter.format(
                        fact(
                                List.of(
                                        uld("AKE12345HJ", "CL", "1250.5", "KG", "AMS"),
                                        uld("PMC00001HJ", "CR", "2200", "LB", "ATH"))));

        assertThat(cpm.type()).isEqualTo("CPM");
        assertThat(cpm.formatVersion()).isEqualTo(1);
        JsonNode payload = JSON.readTree(cpm.payload());
        assertThat(payload.get("flightNumber").asString()).isEqualTo("HJ221");
        assertThat(payload.get("scheduledDepartureDate").asString()).isEqualTo("2026-11-20");
        assertThat(payload.get("departureAirportCode").asString()).isEqualTo("BRU");
        assertThat(payload.get("ulds")).hasSize(2);
        JsonNode first = payload.get("ulds").get(0);
        assertThat(first.get("uldCode").asString()).isEqualTo("AKE12345HJ");
        assertThat(first.get("position").asString()).isEqualTo("CL");
        assertThat(first.get("weight").get("value").decimalValue()).isEqualByComparingTo("1250.5");
        assertThat(first.get("weight").get("unit").asString()).isEqualTo("KG");
        assertThat(first.get("destinationAirportCode").asString()).isEqualTo("AMS");
        // no conversion, the unit stays as booked
        assertThat(payload.get("ulds").get(1).get("weight").get("unit").asString()).isEqualTo("LB");
    }

    @Test
    void loadWithoutPositionedCargoGivesAnEmptyList() {
        JsonNode payload = JSON.readTree(formatter.format(fact(List.of())).payload());

        assertThat(payload.get("ulds").isArray()).isTrue();
        assertThat(payload.get("ulds")).isEmpty();
    }

    @Test
    void serviceHandsTheCpmToThePublisherWithTheCargoMessageId() {
        var publisher = mock(MessagePublisher.class);
        var service =
                new MessageFormattingService(
                        new FlmFormatter(JSON),
                        new MvtEdFormatter(),
                        new MvtEaFormatter(),
                        formatter,
                        new MvtAdFormatter(),
                        new MvtAaFormatter(),
                        new FlightReplannedFormatter(JSON),
                        publisher);
        var sourceId = UUID.randomUUID();
        LoadFinalizedFact fact = fact(List.of());

        service.prepareCpm(sourceId, fact);

        verify(publisher).publish(sourceId, List.of(formatter.format(fact)));
    }

    @Test
    void factRefusesAnInvalidUld() {
        assertThatThrownBy(() -> uld("AKE12345HJ", "CL", "0", "KG", "AMS"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("weight must be greater than zero");
        assertThatThrownBy(() -> uld("AKE12345HJ", " ", "1", "KG", "AMS"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("loading position must not be blank");
        assertThatThrownBy(() -> uld("AKE12345HJ", "CL", "1", "KG", "ams"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("airport code must be a three-letter IATA code");
    }

    private static LoadFinalizedFact fact(List<LoadFinalizedFact.FinalizedUld> ulds) {
        return new LoadFinalizedFact(
                "HJ221",
                LocalDate.of(2026, 11, 20),
                "BRU",
                ulds,
                Instant.parse("2026-11-20T06:55:00Z"));
    }

    private static LoadFinalizedFact.FinalizedUld uld(
            String code, String position, String weight, String unit, String destination) {
        return new LoadFinalizedFact.FinalizedUld(
                code, position, new BigDecimal(weight), unit, destination);
    }
}
