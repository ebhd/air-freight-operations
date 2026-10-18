package be.kdg.airfreight.cargo.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Owns the bookings for one flight and protects their per-flight uniqueness. */
public final class CargoPlan {
    private final FlightKey flightKey;
    private final Map<UldCode, UldBooking> bookings = new LinkedHashMap<>();

    public CargoPlan(FlightKey flightKey) {
        this.flightKey = Objects.requireNonNull(flightKey, "Flight key is required");
    }

    public static CargoPlan restore(FlightKey flightKey, List<UldBooking> bookings) {
        CargoPlan cargoPlan = new CargoPlan(flightKey);
        for (UldBooking booking : bookings) {
            cargoPlan.bookings.put(booking.getCode(), booking);
        }
        return cargoPlan;
    }

    /**
     * Adds an unpositioned booking. The caller must first verify that the flight exists and is
     * planned. Returns false for a duplicate so the caller can log it; existing data is preserved.
     */
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

    public FlightKey getFlightKey() {
        return flightKey;
    }

    public List<UldBooking> getBookings() {
        return List.copyOf(bookings.values());
    }
}
