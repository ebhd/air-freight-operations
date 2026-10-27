package be.kdg.airfreight.cargo.domain.loading;

import java.time.Instant;

// half open so the window is open at opensAt and already closed at closesAt
public record LoadingWindow(Instant opensAt, Instant closesAt) {
    public boolean hasNotOpenedAt(Instant moment) {
        return moment.isBefore(opensAt);
    }

    public boolean hasClosedAt(Instant moment) {
        return !moment.isBefore(closesAt);
    }
}
