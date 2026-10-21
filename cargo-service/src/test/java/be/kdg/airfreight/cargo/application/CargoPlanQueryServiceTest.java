package be.kdg.airfreight.cargo.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import be.kdg.airfreight.cargo.domain.AirportCode;
import be.kdg.airfreight.cargo.domain.CargoPlan;
import be.kdg.airfreight.cargo.domain.CargoPlanRepository;
import be.kdg.airfreight.cargo.domain.FlightInformation;
import be.kdg.airfreight.cargo.domain.FlightKey;
import be.kdg.airfreight.cargo.domain.FlightLookupUnavailableException;
import be.kdg.airfreight.cargo.domain.FlightRepository;
import be.kdg.airfreight.cargo.domain.FlightStatus;
import be.kdg.airfreight.cargo.domain.UldBooking;
import be.kdg.airfreight.cargo.domain.UldCode;
import be.kdg.airfreight.cargo.domain.Weight;
import be.kdg.airfreight.cargo.domain.WeightUnit;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CargoPlanQueryServiceTest {
    private static final FlightKey KEY =
            new FlightKey("HJ221", LocalDate.of(2026, 10, 19), new AirportCode("BRU"));

    private final FlightRepository flights = mock(FlightRepository.class);
    private final CargoPlanRepository cargoPlans = mock(CargoPlanRepository.class);
    private final CargoPlanQueryService service = new CargoPlanQueryService(flights, cargoPlans);

    @Test
    void returnsFlightAndAllBookings() {
        FlightInformation flight = flight(FlightStatus.PLANNED);
        UldBooking first = booking("AKE12345KL", "AMS");
        UldBooking second = booking("PMC00001KL", "ATH");
        when(flights.findByFlightKey(KEY)).thenReturn(Optional.of(flight));
        when(cargoPlans.findByFlightKey(KEY))
                .thenReturn(Optional.of(CargoPlan.restore(KEY, List.of(first, second))));

        CargoPlanView view = service.getCargoPlan(KEY);

        assertThat(view.flight()).isEqualTo(flight);
        assertThat(view.cargoPlan().getBookings()).containsExactly(first, second);
    }

    @Test
    void flightWithoutBookingsHasAnEmptyPlan() {
        when(flights.findByFlightKey(KEY)).thenReturn(Optional.of(flight(FlightStatus.PLANNED)));
        when(cargoPlans.findByFlightKey(KEY)).thenReturn(Optional.empty());

        CargoPlanView view = service.getCargoPlan(KEY);

        assertThat(view.cargoPlan().getFlightKey()).isEqualTo(KEY);
        assertThat(view.cargoPlan().getBookings()).isEmpty();
    }

    // ac4 reading stays possible after departure
    @ParameterizedTest
    @EnumSource(FlightStatus.class)
    void isReadableInEveryFlightState(FlightStatus status) {
        UldBooking booking = booking("AKE12345KL", "AMS");
        when(flights.findByFlightKey(KEY)).thenReturn(Optional.of(flight(status)));
        when(cargoPlans.findByFlightKey(KEY))
                .thenReturn(Optional.of(CargoPlan.restore(KEY, List.of(booking))));

        CargoPlanView view = service.getCargoPlan(KEY);

        assertThat(view.flight().status()).isEqualTo(status);
        assertThat(view.cargoPlan().getBookings()).containsExactly(booking);
    }

    @Test
    void unknownFlightThrows() {
        when(flights.findByFlightKey(KEY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCargoPlan(KEY))
                .isInstanceOf(UnknownFlightException.class)
                .hasMessage("Unknown flight HJ221/2026-10-19/BRU");
        verifyNoInteractions(cargoPlans);
    }

    @Test
    void flightOperationsDownPropagates() {
        when(flights.findByFlightKey(KEY))
                .thenThrow(new FlightLookupUnavailableException(KEY, null));

        assertThatThrownBy(() -> service.getCargoPlan(KEY))
                .isInstanceOf(FlightLookupUnavailableException.class);
        verifyNoInteractions(cargoPlans);
    }

    private static FlightInformation flight(FlightStatus status) {
        return new FlightInformation(
                KEY,
                new AirportCode("AMS"),
                status,
                Instant.parse("2026-10-19T08:00:00Z"),
                Instant.parse("2026-10-19T10:00:00Z"));
    }

    private static UldBooking booking(String uldCode, String destination) {
        return new UldBooking(
                new UldCode(uldCode),
                new Weight(new BigDecimal("1250.5"), WeightUnit.KG),
                new AirportCode(destination));
    }
}
