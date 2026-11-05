package be.kdg.airfreight.flight.infrastructure.persistence.jpa;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaAirportRepository extends JpaRepository<JpaAirport, String> {
    List<JpaAirport> findAllByOrderByCodeAsc();
}
