package be.kdg.airfreight.cargo.domain.cargoplan;

import be.kdg.airfreight.cargo.domain.InvalidCargoValueException;
import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.domain.loadcheck.LoadCheckReport;
import be.kdg.airfreight.cargo.domain.loadcheck.LoadChecks;
import be.kdg.airfreight.cargo.domain.loading.LoadingContext;
import be.kdg.airfreight.cargo.domain.loading.LoadingNotAllowedException;
import be.kdg.airfreight.cargo.domain.loading.LoadingRefusal;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class CargoPlan {
    private final FlightKey flightKey;
    private final Map<UldCode, UldBooking> bookings = new LinkedHashMap<>();
    private CargoPlanState state;
    // null while the plan is a draft
    private Instant finalizedAt;
    // the new flight the cargo moved to, null unless the plan is replanned
    private FlightKey movedTo;

    public CargoPlan(FlightKey flightKey) {
        this(flightKey, CargoPlanState.DRAFT, null, null);
    }

    private CargoPlan(
            FlightKey flightKey, CargoPlanState state, Instant finalizedAt, FlightKey movedTo) {
        this.flightKey = Objects.requireNonNull(flightKey, "Flight key is required");
        this.state = Objects.requireNonNull(state, "State is required");
        this.finalizedAt = finalizedAt;
        this.movedTo = movedTo;
    }

    public static CargoPlan restore(
            FlightKey flightKey, CargoPlanState state, List<UldBooking> bookings) {
        return restore(flightKey, state, null, null, bookings);
    }

    public static CargoPlan restore(
            FlightKey flightKey,
            CargoPlanState state,
            Instant finalizedAt,
            List<UldBooking> bookings) {
        return restore(flightKey, state, finalizedAt, null, bookings);
    }

    public static CargoPlan restore(
            FlightKey flightKey,
            CargoPlanState state,
            Instant finalizedAt,
            FlightKey movedTo,
            List<UldBooking> bookings) {
        CargoPlan cargoPlan = new CargoPlan(flightKey, state, finalizedAt, movedTo);
        for (UldBooking booking : bookings) {
            cargoPlan.bookings.put(booking.getCode(), booking);
        }
        return cargoPlan;
    }

    // false on a duplicate and the existing booking stays
    public boolean bookUld(UldCode code, Weight weight, AirportCode destination) {
        UldBooking booking = new UldBooking(code, weight, destination);
        // a repeat changes nothing so it stays an ignored duplicate also after finalizing
        if (bookings.containsKey(code)) {
            return false;
        }
        // our reading of q07 and car-14: the finalized load takes no new ulds
        if (isFinalized()) {
            throw new BookingRejectedException(
                    "Cargo plan of flight " + flightKey + " is finalized, no ULD can be added");
        }
        if (isReplanned()) {
            throw new BookingRejectedException(
                    "Cargo of flight "
                            + flightKey
                            + " moved to "
                            + movedTo
                            + ", book on that flight");
        }
        bookings.put(code, booking);
        return true;
    }

    public boolean isReplanned() {
        return state == CargoPlanState.REPLANNED;
    }

    // moves every booking to the plan of the new flight and leaves this plan as the marker
    // false when this plan already moved, so a redelivered event changes nothing
    public boolean moveTo(CargoPlan target) {
        Objects.requireNonNull(target, "Target cargo plan is required");
        if (isReplanned()) {
            return false;
        }
        if (target.isFinalized() || target.isReplanned()) {
            throw new BookingRejectedException(
                    "Cargo of flight "
                            + flightKey
                            + " cannot move to "
                            + target.flightKey
                            + ": that cargo plan is "
                            + target.state);
        }
        for (UldBooking booking : bookings.values()) {
            // the same uld booked on the new flight already stays as it is there
            if (target.bookings.containsKey(booking.getCode())) {
                continue;
            }
            // a taken position is not stolen, the load planner places that uld again
            LoadingPosition position =
                    booking.getPosition()
                            .filter(taken -> target.bookingAt(taken).isEmpty())
                            .orElse(null);
            target.bookings.put(
                    booking.getCode(),
                    new UldBooking(
                            booking.getCode(),
                            booking.getWeight(),
                            booking.getDestination(),
                            position));
        }
        bookings.clear();
        state = CargoPlanState.REPLANNED;
        // finalizedAt stays for the history, the new plan starts as a draft
        movedTo = target.flightKey;
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
        // the flight of a moved plan is replanned, also when the copy has not heard it yet
        if (isReplanned() || !context.flight().planned()) {
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

    // a uld with a position moves and its old position becomes free as our q16 reading
    public void assignPosition(UldCode code, LoadingPosition position, LoadingContext context) {
        requireLoadingAllowed(context);
        UldBooking booking = requireBooking(code);
        if (position == null) {
            throw new InvalidCargoValueException("Loading position is required");
        }
        Optional<UldBooking> occupant = bookingAt(position);
        // the same uld on the same position again changes nothing
        if (occupant.isPresent() && occupant.get() != booking) {
            throw new PositionOccupiedException(flightKey, position, occupant.get().getCode());
        }
        booking.placeAt(position);
    }

    // runs every check on the current load and changes nothing
    public LoadCheckReport checkLoad(LoadingContext context, LoadChecks checks) {
        requireLoadingAllowed(context);
        return checks.run(getBookings(), context.flight().arrivalAirportCode());
    }

    // reruns every check so an older passed report never counts and only hard failures block
    public LoadFinalized finalizeLoad(LoadingContext context, LoadChecks checks) {
        LoadCheckReport report = checkLoad(context, checks);
        if (report.hasHardFailure()) {
            throw new LoadNotFinalizedException(flightKey, report);
        }
        state = CargoPlanState.FINALIZED;
        finalizedAt = context.now();
        return new LoadFinalized(flightKey, finalizedAt, positionedUlds(), report);
    }

    // only positioned ulds go in the cpm, in floor plan order from nose to tail
    private List<LoadFinalized.FinalizedUld> positionedUlds() {
        return bookings.values().stream()
                .filter(booking -> booking.getPosition().isPresent())
                .sorted(Comparator.comparing(booking -> booking.getPosition().orElseThrow()))
                .map(
                        booking ->
                                new LoadFinalized.FinalizedUld(
                                        booking.getCode(),
                                        booking.getPosition().orElseThrow(),
                                        booking.getWeight(),
                                        booking.getDestination()))
                .toList();
    }

    // clearing a uld without a position changes nothing
    public void clearPosition(UldCode code, LoadingContext context) {
        requireLoadingAllowed(context);
        requireBooking(code).clearPosition();
    }

    private UldBooking requireBooking(UldCode code) {
        if (code == null) {
            throw new InvalidCargoValueException("ULD code is required");
        }
        UldBooking booking = bookings.get(code);
        if (booking == null) {
            throw new UldNotBookedException(flightKey, code);
        }
        return booking;
    }

    private Optional<UldBooking> bookingAt(LoadingPosition position) {
        for (UldBooking booking : bookings.values()) {
            if (booking.getPosition().equals(Optional.of(position))) {
                return Optional.of(booking);
            }
        }
        return Optional.empty();
    }

    public FlightKey getFlightKey() {
        return flightKey;
    }

    public CargoPlanState getState() {
        return state;
    }

    public Optional<Instant> getFinalizedAt() {
        return Optional.ofNullable(finalizedAt);
    }

    public Optional<FlightKey> getMovedTo() {
        return Optional.ofNullable(movedTo);
    }

    public List<UldBooking> getBookings() {
        return List.copyOf(bookings.values());
    }
}
