package be.kdg.airfreight.cargo.domain.loadcheck;

import be.kdg.airfreight.cargo.domain.exception.InvalidCargoValueException;

// one line of the report with the reason also when the check passed
public record LoadCheckResult(String name, CheckSeverity severity, boolean passed, String reason) {
    public LoadCheckResult {
        if (name == null || name.isBlank()) {
            throw new InvalidCargoValueException("Check name is required");
        }
        if (severity == null) {
            throw new InvalidCargoValueException("Check severity is required");
        }
        if (reason == null || reason.isBlank()) {
            throw new InvalidCargoValueException("Check reason is required");
        }
    }

    public boolean hardFailure() {
        return !passed && severity == CheckSeverity.HARD;
    }
}
