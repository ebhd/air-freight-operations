package be.kdg.airfreight.cargo.application;

import be.kdg.airfreight.cargo.domain.BookingRejectedException;
import be.kdg.airfreight.cargo.domain.CargoPlan;
import be.kdg.airfreight.cargo.domain.CargoPlanRepository;
import be.kdg.airfreight.cargo.domain.DayOfMonthDateRule;
import be.kdg.airfreight.cargo.domain.FlightInformation;
import be.kdg.airfreight.cargo.domain.FlightKey;
import be.kdg.airfreight.cargo.domain.FlightRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Books a received ULD on its flight. Throws {@link BookingRejectedException} or {@link
 * IllegalArgumentException} for input that may not be applied; other exceptions are failures.
 */
@Service
public class BookUldService {
    private static final Logger LOGGER = LoggerFactory.getLogger(BookUldService.class);

    private final FlightRepository flightRepository;
    private final CargoPlanRepository cargoPlanRepository;
    private final ProcessedMessageRepository processedMessageRepository;

    public BookUldService(
            FlightRepository flightRepository,
            CargoPlanRepository cargoPlanRepository,
            ProcessedMessageRepository processedMessageRepository) {
        this.flightRepository = flightRepository;
        this.cargoPlanRepository = cargoPlanRepository;
        this.processedMessageRepository = processedMessageRepository;
    }

    @Transactional
    public BookUldResult bookUld(BookUldCommand command) {
        Optional<ProcessedMessage> earlier =
                processedMessageRepository.findByMessageId(command.messageId());
        if (earlier.isPresent()) {
            LOGGER.info(
                    "Ignored redelivered ULD message {} for flight {}",
                    command.messageId(),
                    earlier.get().flightKey());
            return new BookUldResult(
                    earlier.get().flightKey(), BookUldResult.Outcome.ALREADY_PROCESSED);
        }

        LocalDate scheduledDepartureDate =
                DayOfMonthDateRule.resolve(
                        command.scheduledDepartureDay(), command.firstReceivedAt());
        FlightKey flightKey =
                new FlightKey(
                        command.flightNumber(),
                        scheduledDepartureDate,
                        command.departureAirportCode());
        requirePlannedFlight(flightKey);

        CargoPlan cargoPlan =
                cargoPlanRepository
                        .findByFlightKey(flightKey)
                        .orElseGet(() -> new CargoPlan(flightKey));
        BookUldResult.Outcome outcome;
        if (cargoPlan.bookUld(command.uldCode(), command.weight(), command.destination())) {
            cargoPlanRepository.save(cargoPlan);
            outcome = BookUldResult.Outcome.BOOKED;
            LOGGER.info("Booked ULD {} on flight {}", command.uldCode().value(), flightKey);
        } else {
            outcome = BookUldResult.Outcome.DUPLICATE_ULD;
            LOGGER.info(
                    "Ignored duplicate ULD {} on flight {}", command.uldCode().value(), flightKey);
        }

        processedMessageRepository.save(
                new ProcessedMessage(
                        command.messageId(), Instant.now(), flightKey, command.uldCode(), outcome));
        return new BookUldResult(flightKey, outcome);
    }

    private void requirePlannedFlight(FlightKey flightKey) {
        FlightInformation flight =
                flightRepository
                        .findByFlightKey(flightKey)
                        .orElseThrow(
                                () -> new BookingRejectedException("Unknown flight " + flightKey));
        if (!flight.planned()) {
            throw new BookingRejectedException("Flight " + flightKey + " is no longer planned");
        }
    }
}
