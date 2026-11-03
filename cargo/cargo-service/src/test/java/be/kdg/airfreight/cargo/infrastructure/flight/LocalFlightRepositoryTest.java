package be.kdg.airfreight.cargo.infrastructure.flight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import be.kdg.airfreight.cargo.domain.flight.FlightCopyRepository;
import be.kdg.airfreight.cargo.domain.flight.FlightInformation;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.domain.flight.FlightLookupUnavailableException;
import be.kdg.airfreight.cargo.domain.flight.FlightStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class LocalFlightRepositoryTest {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 19);
    private static final FlightKey KEY = new FlightKey("HJ221", DAY, new AirportCode("BRU"));
    private static final Instant NOW = Instant.parse("2026-10-19T06:00:00Z");
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
    private final FlightOperationsClient flightOperations = mock(FlightOperationsClient.class);
    private final Clock clock = mock(Clock.class);
    private final LocalFlightRepository repository =
            new LocalFlightRepository(copies, flightOperations, clock);

    @Test
    void knownFlightComesFromTheCopyWithoutAskingFlight() {
        when(copies.findCopy(KEY)).thenReturn(Optional.of(FLIGHT));

        assertThat(repository.findByFlightKey(KEY)).contains(FLIGHT);

        verifyNoInteractions(flightOperations);
    }

    // the clock is read before flight is asked so a change during the call stays newer
    @Test
    void unknownFlightIsAskedOnceAndKeptWithTheTimeBeforeTheCall() {
        when(copies.findCopy(KEY)).thenReturn(Optional.empty());
        when(clock.instant()).thenReturn(NOW);
        when(flightOperations.fetch(KEY)).thenReturn(Optional.of(FLIGHT));

        assertThat(repository.findByFlightKey(KEY)).contains(FLIGHT);

        InOrder order = inOrder(clock, flightOperations, copies);
        order.verify(clock).instant();
        order.verify(flightOperations).fetch(KEY);
        order.verify(copies).saveIfNewer(FLIGHT, NOW);
    }

    @Test
    void flightUnknownAtFlightOperationsIsNotStored() {
        when(copies.findCopy(KEY)).thenReturn(Optional.empty());
        when(clock.instant()).thenReturn(NOW);
        when(flightOperations.fetch(KEY)).thenReturn(Optional.empty());

        assertThat(repository.findByFlightKey(KEY)).isEmpty();

        verify(copies, never()).saveIfNewer(any(), any());
    }

    @Test
    void outageForAnUnknownFlightIsNotHidden() {
        when(copies.findCopy(KEY)).thenReturn(Optional.empty());
        when(clock.instant()).thenReturn(NOW);
        when(flightOperations.fetch(KEY))
                .thenThrow(new FlightLookupUnavailableException(KEY, null));

        assertThatThrownBy(() -> repository.findByFlightKey(KEY))
                .isInstanceOf(FlightLookupUnavailableException.class);

        verify(copies, never()).saveIfNewer(any(), any());
    }

    @Test
    void dayOverviewReadsOnlyTheCopy() {
        when(copies.findCopiesOn(DAY)).thenReturn(List.of(FLIGHT));

        assertThat(repository.findByScheduledDepartureDate(DAY)).containsExactly(FLIGHT);

        verifyNoInteractions(flightOperations);
    }
}
