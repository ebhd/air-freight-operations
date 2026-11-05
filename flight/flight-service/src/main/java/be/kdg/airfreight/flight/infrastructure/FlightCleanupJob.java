package be.kdg.airfreight.flight.infrastructure;

import be.kdg.airfreight.flight.application.flight.FlightCleanupService;
import be.kdg.airfreight.flight.domain.flight.FlightKey;
import be.kdg.airfreight.flight.domain.flight.RetentionPolicy;
import be.kdg.airfreight.flight.infrastructure.persistence.jpa.JpaOutboxMessageRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// runs at startup and then every flight.retention.cleanup-interval
@Slf4j
@Component
public class FlightCleanupJob {
    private final FlightCleanupService cleanupService;
    private final JpaOutboxMessageRepository outboxRepository;
    private final RetentionPolicy retentionPolicy;
    private final Clock clock;

    public FlightCleanupJob(
            FlightCleanupService cleanupService,
            JpaOutboxMessageRepository outboxRepository,
            RetentionPolicy retentionPolicy,
            Clock clock) {
        this.cleanupService = cleanupService;
        this.outboxRepository = outboxRepository;
        this.retentionPolicy = retentionPolicy;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${flight.retention.cleanup-interval}")
    public void removeExpiredData() {
        try {
            List<FlightKey> expired = cleanupService.expiredFlights();
            for (FlightKey flightKey : expired) {
                cleanupService.remove(flightKey);
            }
            int outboxRows = outboxRepository.deletePublishedBefore(startOfNotExpiredDays());
            log.debug(
                    "Cleanup removed {} flights and {} published outbox messages",
                    expired.size(),
                    outboxRows);
        } catch (Exception exception) {
            log.warn("Cleanup stopped; the next run continues", exception);
        }
    }

    // a published message expires like a flight of the day it was published
    private Instant startOfNotExpiredDays() {
        return retentionPolicy
                .lastExpiredDate(Instant.now(clock))
                .plusDays(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();
    }
}
