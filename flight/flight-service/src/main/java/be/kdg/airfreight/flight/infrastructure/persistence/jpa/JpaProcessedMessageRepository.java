package be.kdg.airfreight.flight.infrastructure.persistence.jpa;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaProcessedMessageRepository extends JpaRepository<JpaProcessedMessage, UUID> {}
