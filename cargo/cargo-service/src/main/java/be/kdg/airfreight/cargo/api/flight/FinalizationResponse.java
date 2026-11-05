package be.kdg.airfreight.cargo.api.flight;

import be.kdg.airfreight.cargo.domain.cargoplan.LoadFinalized;
import java.time.Instant;

// the report shows the soft warnings the load was finalized with
public record FinalizationResponse(Instant finalizedAt, LoadCheckReportResponse report) {
    static FinalizationResponse from(LoadFinalized event) {
        return new FinalizationResponse(
                event.finalizedAt(), LoadCheckReportResponse.from(event.report()));
    }
}
