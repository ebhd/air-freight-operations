package be.kdg.airfreight.cargo.application.cargoplan;

import static org.assertj.core.api.Assertions.assertThat;

import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlan;
import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlanRepository;
import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlanState;
import be.kdg.airfreight.cargo.domain.cargoplan.UldBooking;
import be.kdg.airfreight.cargo.domain.cargoplan.UldCode;
import be.kdg.airfreight.cargo.domain.cargoplan.Weight;
import be.kdg.airfreight.cargo.domain.cargoplan.WeightUnit;
import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MoveCargoPlanServiceTest {
    private static final AirportCode BRU = new AirportCode("BRU");
    private static final FlightKey A = new FlightKey("HJ500", LocalDate.of(2026, 11, 10), BRU);
    private static final FlightKey B = new FlightKey("HJ500A", LocalDate.of(2026, 11, 11), BRU);
    private static final FlightKey C = new FlightKey("HJ500B", LocalDate.of(2026, 11, 12), BRU);

    private final InMemoryPlans plans = new InMemoryPlans();
    private final MoveCargoPlanService service = new MoveCargoPlanService(plans);

    @Test
    void movesTheBookingsAndSavesBothPlans() {
        plans.save(CargoPlan.restore(A, CargoPlanState.DRAFT, List.of(booking("AKE00001HJ"))));

        service.move(A, B);

        assertThat(plans.find(B).getBookings()).hasSize(1);
        assertThat(plans.find(A).getState()).isEqualTo(CargoPlanState.REPLANNED);
        assertThat(plans.find(A).getMovedTo()).contains(B);
    }

    // the marker says where to look even when nothing was booked
    @Test
    void flightWithoutBookingsStillGetsTheMarkerRow() {
        service.move(A, B);

        assertThat(plans.find(A).getState()).isEqualTo(CargoPlanState.REPLANNED);
        assertThat(plans.find(A).getMovedTo()).contains(B);
        assertThat(plans.find(B).getBookings()).isEmpty();
    }

    @Test
    void secondEventChangesNothing() {
        plans.save(CargoPlan.restore(A, CargoPlanState.DRAFT, List.of(booking("AKE00001HJ"))));
        service.move(A, B);
        plans.saves = 0;

        service.move(A, B);

        assertThat(plans.saves).isZero();
    }

    // b was replanned to c before a's move arrived, so a's cargo ends up on c
    @Test
    void followsAChainWhenTheNewFlightWasReplannedToo() {
        plans.save(CargoPlan.restore(A, CargoPlanState.DRAFT, List.of(booking("AKE00001HJ"))));
        plans.save(CargoPlan.restore(B, CargoPlanState.REPLANNED, null, C, List.of()));

        service.move(A, B);

        assertThat(plans.find(C).getBookings()).hasSize(1);
        assertThat(plans.find(A).getMovedTo()).contains(C);
        assertThat(plans.find(B).getBookings()).isEmpty();
    }

    private static UldBooking booking(String code) {
        return new UldBooking(
                new UldCode(code),
                new Weight(new BigDecimal("1000"), WeightUnit.KG),
                new AirportCode("AMS"));
    }

    private static final class InMemoryPlans implements CargoPlanRepository {
        private final Map<FlightKey, CargoPlan> plans = new HashMap<>();
        private int saves;

        @Override
        public Optional<CargoPlan> findByFlightKey(FlightKey flightKey) {
            return Optional.ofNullable(plans.get(flightKey));
        }

        @Override
        public void save(CargoPlan cargoPlan) {
            saves++;
            plans.put(cargoPlan.getFlightKey(), cargoPlan);
        }

        @Override
        public void deleteByFlightKey(FlightKey flightKey) {}

        CargoPlan find(FlightKey key) {
            return plans.get(key);
        }
    }
}
