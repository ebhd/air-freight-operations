package be.kdg.airfreight.cargo.application.booking;

import be.kdg.airfreight.cargo.domain.cargoplan.BookingRejectedException;
import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlan;
import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlanRepository;
import be.kdg.airfreight.cargo.domain.cargoplan.UldBooking;
import be.kdg.airfreight.cargo.domain.flight.DayOfMonthDateRule;
import be.kdg.airfreight.cargo.domain.flight.FlightInformation;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.domain.flight.FlightRepository;
import be.kdg.airfreight.cargo.domain.flight.FlightStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class BookUldService {
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
            log.info(
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
            log.info("Booked ULD {} on flight {}", command.uldCode().value(), flightKey);
        } else {
            outcome = BookUldResult.Outcome.DUPLICATE_ULD;
            logIgnoredRepeat(cargoPlan.findBooking(command.uldCode()).orElseThrow(), command);
        }

        processedMessageRepository.save(
                new ProcessedMessage(
                        command.messageId(), Instant.now(), flightKey, command.uldCode(), outcome));
        return new BookUldResult(flightKey, outcome);
    }

    private static void logIgnoredRepeat(UldBooking existing, BookUldCommand repeat) {
        if (existing.getWeight().equals(repeat.weight())
                && existing.getDestination().equals(repeat.destination())) {
            log.info(
                    "Ignored repeated booking of ULD {} from message {}",
                    existing.getCode().value(),
                    repeat.messageId());
            return;
        }
        log.warn(
                "Ignored repeated booking of ULD {} from message {} with different content: kept"
                        + " {} to {}, ignored {} to {}",
                existing.getCode().value(),
                repeat.messageId(),
                existing.getWeight(),
                existing.getDestination(),
                repeat.weight(),
                repeat.destination());
    }

    private void requirePlannedFlight(FlightKey flightKey) {
        FlightInformation flight =
                flightRepository
                        .findByFlightKey(flightKey)
                        .orElseThrow(
                                () -> new BookingRejectedException("Unknown flight " + flightKey));
        if (flight.status() == FlightStatus.REPLANNED) {
            throw new BookingRejectedException(
                    "Flight " + flightKey + " was replanned, book on the new flight");
        }
        if (!flight.planned()) {
            throw new BookingRejectedException("Flight " + flightKey + " is no longer planned");
        }
    }
}
