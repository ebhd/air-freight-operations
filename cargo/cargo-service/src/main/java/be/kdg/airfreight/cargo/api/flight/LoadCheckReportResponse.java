package be.kdg.airfreight.cargo.api.flight;

import be.kdg.airfreight.cargo.domain.loadcheck.LoadCheckReport;
import be.kdg.airfreight.cargo.domain.loadcheck.LoadCheckResult;
import java.util.List;

// hardChecksPassed tells the page whether finalizing may be offered
public record LoadCheckReportResponse(boolean hardChecksPassed, List<Result> results) {
    public record Result(String name, String severity, boolean passed, String reason) {
        static Result from(LoadCheckResult result) {
            return new Result(
                    result.name(), result.severity().name(), result.passed(), result.reason());
        }
    }

    static LoadCheckReportResponse from(LoadCheckReport report) {
        return new LoadCheckReportResponse(
                !report.hasHardFailure(), report.results().stream().map(Result::from).toList());
    }
}
