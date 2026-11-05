package be.kdg.airfreight.flight.domain.flight.replanning;

import java.util.Optional;

// the replanned flight keeps its number plus a letter so its key is always free
public final class FlightNumberSuffix {
    private FlightNumberSuffix() {}

    // HJ500 gives HJ500A, HJ500A gives HJ500B, after HJ500Z there is no next number
    public static Optional<String> next(String flightNumber) {
        String number = flightNumber.trim();
        char last = number.charAt(number.length() - 1);
        if (last == 'Z') {
            return Optional.empty();
        }
        if (last >= 'A' && last < 'Z') {
            char nextLetter = (char) (last + 1);
            return Optional.of(number.substring(0, number.length() - 1) + nextLetter);
        }
        if (number.length() == 32) {
            return Optional.empty();
        }
        return Optional.of(number + "A");
    }
}
