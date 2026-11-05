package be.kdg.airfreight.formatter.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

// the finalized load from cargo, ulds holds only positioned cargo and may be empty
public record LoadFinalizedFact(
        String flightNumber,
        LocalDate scheduledDepartureDate,
        String departureAirportCode,
        List<FinalizedUld> ulds,
        Instant occurredOn) {
    public LoadFinalizedFact {
        if (flightNumber == null || flightNumber.isBlank()) {
            throw new IllegalArgumentException("flight number must not be blank");
        }
        Objects.requireNonNull(scheduledDepartureDate, "scheduled departure date must not be null");
        requireAirportCode(departureAirportCode);
        Objects.requireNonNull(ulds, "ulds must not be null");
        Objects.requireNonNull(occurredOn, "event occurrence time must not be null");
        ulds = List.copyOf(ulds);
    }

    // the weight keeps the unit it was booked with
    public record FinalizedUld(
            String uldCode,
            String position,
            BigDecimal weightValue,
            String weightUnit,
            String destinationAirportCode) {
        public FinalizedUld {
            requireText(uldCode, "ULD code");
            requireText(position, "loading position");
            requireText(weightUnit, "weight unit");
            Objects.requireNonNull(weightValue, "weight value must not be null");
            if (weightValue.signum() <= 0) {
                throw new IllegalArgumentException("weight must be greater than zero");
            }
            requireAirportCode(destinationAirportCode);
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private static void requireAirportCode(String code) {
        if (code == null || !code.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("airport code must be a three-letter IATA code");
        }
    }
}
