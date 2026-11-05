package be.kdg.airfreight.formatter.domain;

// the checks every fact that ends up in an mvt line 2 needs
final class TelegramText {
    private TelegramText() {}

    // slash and dot separate the parts of mvt line 2 so they cannot be inside a part
    static void requireTelegramText(String value, String name) {
        if (value == null
                || value.isBlank()
                || value.contains("\n")
                || value.contains("\r")
                || value.contains("/")
                || value.contains(".")) {
            throw new IllegalArgumentException(
                    name + " must be nonblank single-line text without / or .");
        }
    }

    static void requireAirportCode(String code) {
        if (code == null || !code.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("airport code must be a three-letter IATA code");
        }
    }
}
