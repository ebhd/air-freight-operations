package be.kdg.airfreight.cargo.infrastructure.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaProcessedMessageRepository extends JpaRepository<JpaProcessedMessage, String> {}
