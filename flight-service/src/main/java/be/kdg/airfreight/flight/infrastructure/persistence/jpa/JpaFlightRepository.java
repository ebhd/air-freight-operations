package be.kdg.airfreight.flight.infrastructure.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaFlightRepository extends JpaRepository<JpaFlight, JpaFlightId> {}
