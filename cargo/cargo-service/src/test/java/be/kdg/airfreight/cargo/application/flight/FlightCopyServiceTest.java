package be.kdg.airfreight.cargo.application.flight;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import be.kdg.airfreight.cargo.domain.exception.FlightLookupUnavailableException;
import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import be.kdg.airfreight.cargo.domain.flight.FlightCopyRepository;
import be.kdg.airfreight.cargo.domain.flight.FlightInformation;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.domain.flight.FlightRepository;
import be.kdg.airfreight.cargo.domain.flight.FlightStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FlightCopyServiceTest {
    private static final FlightKey KEY =
            new FlightKey("HJ221", LocalDate.of(2026, 10, 19), new AirportCode("BRU"));
    private static final Instant CREATED = Instant.parse("2026-10-18T12:00:00Z");
    private static final Instant DELAYED = Instant.parse("2026-10-19T07:00:00Z");
    private static final Instant ETD = Instant.parse("2026-10-19T08:45:00Z");
    private static final Instant ETA = Instant.parse("2026-10-19T09:55:00Z");
    private static final FlightInformation FLIGHT =
            new FlightInformation(
                    KEY,
                    new AirportCode("AMS"),
                    FlightStatus.PLANNED,
                    Instant.parse("2026-10-19T08:00:00Z"),
                    Instant.parse("2026-10-19T09:10:00Z"),
                    Instant.parse("2026-10-19T08:00:00Z"),
                    Instant.parse("2026-10-19T09:10:00Z"));

    private final FlightCopyRepository copies = mock(FlightCopyRepository.class);
    private final FlightRepository flights = mock(FlightRepository.class);
    private final FlightCopyService service = new FlightCopyService(copies, flights);

    @Test
    void createdFlightIsStoredWithTheEventTime() {
        service.recordCreated(FLIGHT, CREATED);

        verify(copies).saveIfNewer(FLIGHT, CREATED);
        verifyNoInteractions(flights);
    }

    // only the estimates so the rest of the copy is never written from an estimate event
    @Test
    void newEstimatesAreAppliedToTheCopy() {
        when(flights.findByFlightKey(KEY)).thenReturn(Optional.of(FLIGHT));

        service.recordEstimates(KEY, ETD, ETA, DELAYED);

        verify(copies).saveEstimatesIfNewer(KEY, ETD, ETA, DELAYED);
        verify(copies, never()).saveIfNewer(any(), any());
    }

    // the status moves forward without a time, the repository refuses what is not earlier
    @Test
    void departedMovesTheStatusOfAKnownCopy() {
        when(flights.findByFlightKey(KEY)).thenReturn(Optional.of(FLIGHT));

        service.recordDeparted(KEY);

        verify(copies).advanceStatus(KEY, FlightStatus.DEPARTED);
    }

    @Test
    void landedMovesTheStatusOfAKnownCopy() {
        when(flights.findByFlightKey(KEY)).thenReturn(Optional.of(FLIGHT));

        service.recordLanded(KEY);

        verify(copies).advanceStatus(KEY, FlightStatus.LANDED);
    }

    @Test
    void departedForAFlightFlightDoesNotKnowChangesNothing() {
        when(flights.findByFlightKey(KEY)).thenReturn(Optional.empty());

        service.recordDeparted(KEY);

        verify(copies, never()).advanceStatus(any(), any());
    }

    // flight down: the exception reaches the listener, which lets rabbit retry
    @Test
    void departedWhileFlightIsDownPropagatesTheOutage() {
        when(flights.findByFlightKey(KEY))
                .thenThrow(new FlightLookupUnavailableException(KEY, null));

        assertThatThrownBy(() -> service.recordDeparted(KEY))
                .isInstanceOf(FlightLookupUnavailableException.class);
        verify(copies, never()).advanceStatus(any(), any());
    }

    @Test
    void estimatesForAFlightFlightDoesNotKnowStoreNothing() {
        when(flights.findByFlightKey(KEY)).thenReturn(Optional.empty());

        service.recordEstimates(KEY, ETD, ETA, DELAYED);

        verify(copies, never()).saveIfNewer(any(), any());
        verify(copies, never()).saveEstimatesIfNewer(any(), any(), any(), any());
    }

    @Test
    void replannedFlightGetsTheStatusInTheCopy() {
        when(flights.findByFlightKey(KEY)).thenReturn(Optional.of(FLIGHT));

        service.recordReplanned(KEY);

        verify(copies).advanceStatus(KEY, FlightStatus.REPLANNED);
    }
}
