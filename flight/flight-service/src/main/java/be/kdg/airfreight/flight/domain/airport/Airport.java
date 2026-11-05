package be.kdg.airfreight.flight.domain.airport;

public record Airport(String code, String name) {
    public Airport {
        code = requireText(code, "airport code");
        name = requireText(name, "airport name");
    }

    // airports only come from our own table so a broken row is a server error
    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(label + " must not be blank");
        }
        return value.trim();
    }
}
