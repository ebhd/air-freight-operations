package be.kdg.airfreight.cargo.application.flight;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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

    @Test
    void newEstimatesAreAppliedToTheCopy() {
        when(copies.findCopy(KEY)).thenReturn(Optional.of(FLIGHT));

        service.recordEstimates(KEY, ETD, ETA, DELAYED);

        verify(copies).saveIfNewer(FLIGHT.withEstimates(ETD, ETA), DELAYED);
        verifyNoInteractions(flights);
    }

    // the repository decides with the event time, the service passes it on unchanged
    @Test
    void olderEstimatesAreLeftToTheRepositoryToRefuse() {
        when(copies.findCopy(KEY)).thenReturn(Optional.of(FLIGHT));
        when(copies.saveIfNewer(any(), any())).thenReturn(false);

        service.recordEstimates(KEY, ETD, ETA, CREATED);

        verify(copies).saveIfNewer(FLIGHT.withEstimates(ETD, ETA), CREATED);
    }

    // the lookup at flight already brings the current estimates and keeps them in the copy
    @Test
    void estimatesForAFlightNotInTheCopyAskFlightOnce() {
        when(copies.findCopy(KEY)).thenReturn(Optional.empty());
        when(flights.findByFlightKey(KEY)).thenReturn(Optional.of(FLIGHT));

        service.recordEstimates(KEY, ETD, ETA, DELAYED);

        verify(flights).findByFlightKey(KEY);
        verify(copies, never()).saveIfNewer(any(), any());
    }

    @Test
    void estimatesForAFlightFlightDoesNotKnowStoreNothing() {
        when(copies.findCopy(KEY)).thenReturn(Optional.empty());
        when(flights.findByFlightKey(KEY)).thenReturn(Optional.empty());

        service.recordEstimates(KEY, ETD, ETA, DELAYED);

        verify(copies, never()).saveIfNewer(any(), any());
    }
}
