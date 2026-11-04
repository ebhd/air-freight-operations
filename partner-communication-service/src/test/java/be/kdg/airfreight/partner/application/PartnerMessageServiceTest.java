package be.kdg.airfreight.partner.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import be.kdg.airfreight.partner.domain.FlightCreatedFact;
import be.kdg.airfreight.partner.domain.FlightEstimatesChangedFact;
import be.kdg.airfreight.partner.domain.PartnerMessage;
import be.kdg.airfreight.partner.domain.PartnerMessageRepository;
import be.kdg.airfreight.partner.domain.PublicationState;
import be.kdg.airfreight.partner.infrastructure.formatting.FlmFormatter;
import be.kdg.airfreight.partner.infrastructure.formatting.MvtEaFormatter;
import be.kdg.airfreight.partner.infrastructure.formatting.MvtEdFormatter;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.json.JsonMapper;

class PartnerMessageServiceTest {
    private final PartnerMessageRepository repository = mock(PartnerMessageRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final FlmFormatter formatter = new FlmFormatter(mapper);
    private final PartnerMessageService service =
            new PartnerMessageService(
                    repository, formatter, new MvtEdFormatter(), new MvtEaFormatter(), events);

    @Test
    void createsPendingFlmWithRequiredFlightContent() {
        var sourceId = UUID.randomUUID();
        var fact = fact();
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var message = service.prepareFlm(sourceId, fact);

        assertThat(message.id().value()).isNotNull();
        assertThat(message.createdAtUTC()).isNotNull();
        assertThat(message.sourceMessageId()).isEqualTo(sourceId);
        assertThat(message.state()).isEqualTo(PublicationState.PENDING);
        assertThat(message.content().type()).isEqualTo("FLM");
        assertThat(message.content().formatVersion()).isEqualTo(1);
        var payload = mapper.readTree(message.content().payload());
        assertThat(payload.get("flightNumber").asText()).isEqualTo(fact.flightNumber());
        assertThat(payload.get("scheduledDepartureDate").asText())
                .isEqualTo(fact.scheduledDepartureDate().toString());
        assertThat(payload.get("departureAirportCode").asText())
                .isEqualTo(fact.departureAirportCode());
        assertThat(payload.get("arrivalAirportCode").asText()).isEqualTo(fact.arrivalAirportCode());
        assertThat(payload.get("scheduledDeparture").asText())
                .isEqualTo(fact.scheduledDeparture().toString());
        assertThat(payload.get("scheduledArrival").asText())
                .isEqualTo(fact.scheduledArrival().toString());
        verify(repository).save(message);
        verify(events).publishEvent(message);
    }

    @Test
    void redeliveredEventKeepsOriginalFlmIdentityAndDoesNotCreateAnother() {
        var sourceId = UUID.randomUUID();
        var existing =
                PartnerMessage.create(
                        sourceId, Instant.parse("2026-10-19T08:00:00Z"), formatter.format(fact()));
        when(repository.findBySourceMessageIdAndType(sourceId, "FLM"))
                .thenReturn(Optional.of(existing));

        assertThat(service.prepareFlm(sourceId, fact())).isSameAs(existing);

        verify(repository, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test
    void changedDepartureEstimatePreparesEdAndEa() {
        var sourceId = UUID.randomUUID();
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.prepareEstimates(
                sourceId,
                estimates(
                        Instant.parse("2026-10-20T08:20:00Z"),
                        Instant.parse("2026-10-20T10:20:00Z")));

        assertThat(savedTypes(2)).containsExactly("MVT_ED", "MVT_EA");
    }

    @Test
    void changedArrivalEstimateOnlyPreparesEa() {
        var sourceId = UUID.randomUUID();
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.prepareEstimates(
                sourceId,
                estimates(
                        Instant.parse("2026-10-20T08:00:00Z"),
                        Instant.parse("2026-10-20T10:15:00Z")));

        assertThat(savedTypes(1)).containsExactly("MVT_EA");
    }

    @Test
    void redeliveredEstimatesPrepareNoSecondEdOrEa() {
        var sourceId = UUID.randomUUID();
        var fact =
                estimates(
                        Instant.parse("2026-10-20T08:20:00Z"),
                        Instant.parse("2026-10-20T10:20:00Z"));
        var created = Instant.parse("2026-10-19T08:00:00Z");
        when(repository.findBySourceMessageIdAndType(sourceId, "MVT_ED"))
                .thenReturn(
                        Optional.of(
                                PartnerMessage.create(
                                        sourceId, created, new MvtEdFormatter().format(fact))));
        when(repository.findBySourceMessageIdAndType(sourceId, "MVT_EA"))
                .thenReturn(
                        Optional.of(
                                PartnerMessage.create(
                                        sourceId, created, new MvtEaFormatter().format(fact))));

        service.prepareEstimates(sourceId, fact);

        verify(repository, never()).save(any());
        verifyNoInteractions(events);
    }

    private List<String> savedTypes(int count) {
        var saved = ArgumentCaptor.forClass(PartnerMessage.class);
        verify(repository, times(count)).save(saved.capture());
        return saved.getAllValues().stream().map(message -> message.content().type()).toList();
    }

    // scheduled 08:00 to 10:00 utc and no earlier delay
    private FlightEstimatesChangedFact estimates(
            Instant estimatedDeparture, Instant estimatedArrival) {
        return new FlightEstimatesChangedFact(
                "HJ221",
                LocalDate.of(2026, 10, 20),
                "BRU",
                "AMS",
                "SXBAA",
                Instant.parse("2026-10-20T08:00:00Z"),
                Instant.parse("2026-10-20T10:00:00Z"),
                estimatedDeparture,
                estimatedArrival,
                Instant.parse("2026-10-19T07:00:00Z"));
    }

    private FlightCreatedFact fact() {
        return new FlightCreatedFact(
                "HJ221",
                LocalDate.of(2026, 10, 20),
                "BRU",
                "AMS",
                Instant.parse("2026-10-20T08:00:00Z"),
                Instant.parse("2026-10-20T10:00:00Z"),
                Instant.parse("2026-10-19T07:00:00Z"));
    }
}
