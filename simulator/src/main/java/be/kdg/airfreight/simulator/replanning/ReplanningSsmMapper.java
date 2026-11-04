package be.kdg.airfreight.simulator.replanning;

import be.kdg.airfreight.simulator.ssm.SsmMessage;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;

// turns the new schedule into a normal one-day ssm, the format flight already understands
final class ReplanningSsmMapper {
    private ReplanningSsmMapper() {}

    // message id replan-<event id> so a redelivered event gives the same ssm (flight skips it)
    static SsmMessage toSsm(FlightReplannedMessage event) {
        LocalDateTime departure =
                LocalDateTime.ofInstant(event.newScheduledDeparture(), ZoneOffset.UTC);
        LocalDateTime arrival =
                LocalDateTime.ofInstant(event.newScheduledArrival(), ZoneOffset.UTC);
        // flight reads an arrival time not after the departure time as the next day
        return new SsmMessage(
                "replan-" + event.messageId(),
                departure.toLocalDate(),
                departure.toLocalDate(),
                Set.of(departure.getDayOfWeek()),
                departure.toLocalTime(),
                arrival.toLocalTime(),
                event.newFlightNumber(),
                event.departureAirportCode(),
                event.arrivalAirportCode(),
                event.aircraftRegistration());
    }
}
