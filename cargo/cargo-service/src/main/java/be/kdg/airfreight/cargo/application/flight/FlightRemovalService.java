package be.kdg.airfreight.cargo.application.flight;

import be.kdg.airfreight.cargo.application.booking.ProcessedMessageRepository;
import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlanRepository;
import be.kdg.airfreight.cargo.domain.flight.FlightCopyRepository;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// flight operations owns the lifecycle, so cargo removes its data when flight says the flight is
// gone; a redelivery finds nothing and changes nothing
@Service
@Slf4j
public class FlightRemovalService {
    private final CargoPlanRepository cargoPlanRepository;
    private final ProcessedMessageRepository processedMessages;
    private final FlightCopyRepository copies;

    public FlightRemovalService(
            CargoPlanRepository cargoPlanRepository,
            ProcessedMessageRepository processedMessages,
            FlightCopyRepository copies) {
        this.cargoPlanRepository = cargoPlanRepository;
        this.processedMessages = processedMessages;
        this.copies = copies;
    }

    // one transaction: the plan with its bookings, the dedup rows and the copy go together
    @Transactional
    public void remove(FlightKey flightKey) {
        cargoPlanRepository.deleteByFlightKey(flightKey);
        processedMessages.deleteByFlightKey(flightKey);
        copies.delete(flightKey);
        log.info("Removed the cargo data of flight {}", flightKey);
    }
}
