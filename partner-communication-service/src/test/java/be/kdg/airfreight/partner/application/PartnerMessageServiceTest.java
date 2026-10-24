package be.kdg.airfreight.partner.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import be.kdg.airfreight.partner.domain.FlightCreatedFact;
import be.kdg.airfreight.partner.domain.PartnerMessage;
import be.kdg.airfreight.partner.domain.PartnerMessageRepository;
import be.kdg.airfreight.partner.domain.PublicationState;
import be.kdg.airfreight.partner.infrastructure.formatting.FlmFormatter;
import be.kdg.airfreight.partner.infrastructure.formatting.MvtEaFormatter;
import be.kdg.airfreight.partner.infrastructure.formatting.MvtEdFormatter;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
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
