package be.kdg.airfreight.cargo.application.cargoplan;

import static be.kdg.airfreight.cargo.support.TestHelper.restoredPlan;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlanRepository;
import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlanState;
import be.kdg.airfreight.cargo.domain.cargoplan.UldBooking;
import be.kdg.airfreight.cargo.domain.cargoplan.UldCode;
import be.kdg.airfreight.cargo.domain.cargoplan.Weight;
import be.kdg.airfreight.cargo.domain.cargoplan.WeightUnit;
import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import be.kdg.airfreight.cargo.domain.flight.FlightInformation;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.domain.flight.FlightRepository;
import be.kdg.airfreight.cargo.domain.flight.FlightStatus;
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
    void flightWithoutBookingsHasAnEmptyPlan() {
        when(flights.findByFlightKey(KEY)).thenReturn(Optional.of(flight(FlightStatus.PLANNED)));
        when(cargoPlans.findByFlightKey(KEY)).thenReturn(Optional.empty());

        CargoPlanView view = service.getCargoPlan(KEY);

        assertThat(view.cargoPlan().getFlightKey()).isEqualTo(KEY);
        assertThat(view.cargoPlan().getBookings()).isEmpty();
    }

    // reading stays possible after departure
    @ParameterizedTest
    @EnumSource(FlightStatus.class)
    void isReadableInEveryFlightState(FlightStatus status) {
        UldBooking booking = booking("AKE12345KL", "AMS");
        when(flights.findByFlightKey(KEY)).thenReturn(Optional.of(flight(status)));
        when(cargoPlans.findByFlightKey(KEY))
                .thenReturn(Optional.of(restoredPlan(KEY, CargoPlanState.DRAFT, List.of(booking))));

        CargoPlanView view = service.getCargoPlan(KEY);

        assertThat(view.flight().status()).isEqualTo(status);
        assertThat(view.cargoPlan().getBookings()).containsExactly(booking);
    }

    private static FlightInformation flight(FlightStatus status) {
        return new FlightInformation(
                KEY,
                new AirportCode("AMS"),
                status,
                Instant.parse("2026-10-19T08:00:00Z"),
                Instant.parse("2026-10-19T10:00:00Z"),
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
