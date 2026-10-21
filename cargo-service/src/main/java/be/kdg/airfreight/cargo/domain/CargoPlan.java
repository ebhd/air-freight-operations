package be.kdg.airfreight.cargo.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class CargoPlan {
    private final FlightKey flightKey;
    private final Map<UldCode, UldBooking> bookings = new LinkedHashMap<>();
    // only restore can give finalized until finalizing exists in issue 26
    private final CargoPlanState state;

    public CargoPlan(FlightKey flightKey) {
        this(flightKey, CargoPlanState.DRAFT);
    }

    private CargoPlan(FlightKey flightKey, CargoPlanState state) {
        this.flightKey = Objects.requireNonNull(flightKey, "Flight key is required");
        this.state = state;
    }

    public static CargoPlan restore(
            FlightKey flightKey, CargoPlanState state, List<UldBooking> bookings) {
        CargoPlan cargoPlan = new CargoPlan(flightKey, state);
        for (UldBooking booking : bookings) {
            cargoPlan.bookings.put(booking.getCode(), booking);
        }
        return cargoPlan;
    }

    // false on a duplicate and the existing booking stays
    public boolean bookUld(UldCode code, Weight weight, AirportCode destination) {
        UldBooking booking = new UldBooking(code, weight, destination);
        if (bookings.containsKey(code)) {
            return false;
        }
        bookings.put(code, booking);
        return true;
    }

    public Optional<UldBooking> findBooking(UldCode code) {
        return Optional.ofNullable(bookings.get(code));
    }

    public boolean isFinalized() {
        return state == CargoPlanState.FINALIZED;
    }

    // the one rule for changing checking and finalizing a load
    public Optional<LoadingRefusal> loadingRefusal(LoadingContext context) {
        FlightKey contextFlightKey = context.flight().flightKey();
        if (!contextFlightKey.equals(flightKey)) {
            throw new InvalidCargoValueException(
                    "Loading context of "
                            + contextFlightKey
                            + " is not for cargo plan "
                            + flightKey);
        }
        // first because a later delay must never reopen a finalized plan
        if (isFinalized()) {
            return Optional.of(LoadingRefusal.PLAN_FINALIZED);
        }
        if (!context.flight().planned()) {
            return Optional.of(LoadingRefusal.FLIGHT_NOT_PLANNED);
        }
        if (context.window().hasNotOpenedAt(context.now())) {
            return Optional.of(LoadingRefusal.WINDOW_NOT_OPEN_YET);
        }
        if (context.window().hasClosedAt(context.now())) {
            return Optional.of(LoadingRefusal.WINDOW_CLOSED);
        }
        return Optional.empty();
    }

    // positions checks and finalizing call this first and booking does not
    public void requireLoadingAllowed(LoadingContext context) {
        Optional<LoadingRefusal> refusal = loadingRefusal(context);
        if (refusal.isPresent()) {
            throw new LoadingNotAllowedException(flightKey, refusal.get(), context.window());
        }
    }

    public FlightKey getFlightKey() {
        return flightKey;
    }

    public List<UldBooking> getBookings() {
        return List.copyOf(bookings.values());
    }
}
