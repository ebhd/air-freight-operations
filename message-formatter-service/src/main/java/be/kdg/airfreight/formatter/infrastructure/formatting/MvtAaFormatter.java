package be.kdg.airfreight.formatter.infrastructure.formatting;

import be.kdg.airfreight.formatter.domain.FlightLandedFact;
import be.kdg.airfreight.formatter.domain.MessageContent;
import be.kdg.airfreight.formatter.domain.MessageFormatter;
import org.springframework.stereotype.Component;

@Component
public class MvtAaFormatter implements MessageFormatter<FlightLandedFact> {
    private static final int FORMAT_VERSION = 1;

    @Override
    public MessageContent format(FlightLandedFact fact) {
        String telegram =
                MvtTelegram.lines(
                        fact.flightNumber(),
                        fact.scheduledDepartureDate(),
                        fact.aircraftRegistration(),
                        fact.arrivalAirportCode(),
                        "AA",
                        fact.actualArrival());
        return new MessageContent("MVT_AA", telegram, FORMAT_VERSION);
    }
}
