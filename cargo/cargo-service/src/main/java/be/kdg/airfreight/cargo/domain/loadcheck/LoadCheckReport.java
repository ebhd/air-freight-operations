package be.kdg.airfreight.cargo.domain.loadcheck;

import be.kdg.airfreight.cargo.domain.exception.InvalidCargoValueException;
import java.util.List;

// every check is in the report also after an earlier one failed
public record LoadCheckReport(List<LoadCheckResult> results) {
    public LoadCheckReport {
        if (results == null) {
            throw new InvalidCargoValueException("Check results are required");
        }
        results = List.copyOf(results);
    }

    // soft warnings never block finalizing
    public boolean hasHardFailure() {
        return results.stream().anyMatch(LoadCheckResult::hardFailure);
    }
}
