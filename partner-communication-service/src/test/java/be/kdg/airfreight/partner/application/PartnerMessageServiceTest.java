package be.kdg.airfreight.partner.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import be.kdg.airfreight.partner.domain.MessageContent;
import be.kdg.airfreight.partner.domain.PartnerMessage;
import be.kdg.airfreight.partner.domain.PartnerMessageRepository;
import be.kdg.airfreight.partner.domain.PublicationState;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class PartnerMessageServiceTest {
    private final PartnerMessageRepository repository = mock(PartnerMessageRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-05T10:00:00Z"), ZoneOffset.UTC);
    private final PartnerMessageService service =
            new PartnerMessageService(repository, events, clock);

    @Test
    void createsPendingFlmWithThePreparedContent() {
        var sourceId = UUID.randomUUID();
        var content = flm();
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.prepareMessages(sourceId, List.of(content));
        var captor = ArgumentCaptor.forClass(PartnerMessage.class);
        verify(repository).save(captor.capture());
        var message = captor.getValue();

        assertThat(message.id().value()).isNotNull();
        assertThat(message.createdAtUTC()).isEqualTo(clock.instant());
        assertThat(message.sourceMessageId()).isEqualTo(sourceId);
        assertThat(message.state()).isEqualTo(PublicationState.PENDING);
        assertThat(message.content()).isEqualTo(content);
        verify(repository).save(message);
        verify(events).publishEvent(message);
    }

    @Test
    void redeliveredEventKeepsOriginalFlmIdentityAndDoesNotCreateAnother() {
        var sourceId = UUID.randomUUID();
        var existing =
                PartnerMessage.create(sourceId, Instant.parse("2026-10-19T08:00:00Z"), flm());
        when(repository.findBySourceMessageIdAndType(sourceId, "FLM"))
                .thenReturn(Optional.of(existing));

        service.prepareMessages(sourceId, List.of(flm()));

        verify(repository, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test
    void redeliveredEstimatesPrepareNoSecondEdOrEa() {
        var sourceId = UUID.randomUUID();
        var created = Instant.parse("2026-10-19T08:00:00Z");
        when(repository.findBySourceMessageIdAndType(sourceId, "MVT_ED"))
                .thenReturn(Optional.of(PartnerMessage.create(sourceId, created, ed())));
        when(repository.findBySourceMessageIdAndType(sourceId, "MVT_EA"))
                .thenReturn(Optional.of(PartnerMessage.create(sourceId, created, ea())));

        service.prepareMessages(sourceId, List.of(ed(), ea()));

        verify(repository, never()).save(any());
        verifyNoInteractions(events);
    }

    private MessageContent ed() {
        return new MessageContent("MVT_ED", "MVT\nHJ221/20.SXBAA.BRU\nED0820", 1);
    }

    private MessageContent ea() {
        return new MessageContent("MVT_EA", "MVT\nHJ221/20.SXBAA.AMS\nEA1020", 1);
    }

    private MessageContent flm() {
        return new MessageContent(
                "FLM",
                """
                {"flightNumber":"HJ221","scheduledDepartureDate":"2026-10-20",
                 "departureAirportCode":"BRU","arrivalAirportCode":"AMS",
                 "scheduledDeparture":"2026-10-20T08:00:00Z","scheduledArrival":"2026-10-20T10:00:00Z"}
                """,
                1);
    }
}
