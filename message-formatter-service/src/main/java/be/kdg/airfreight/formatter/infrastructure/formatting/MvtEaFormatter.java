package be.kdg.airfreight.formatter.infrastructure.formatting;

import be.kdg.airfreight.formatter.domain.FlightEstimatesChangedFact;
import be.kdg.airfreight.formatter.domain.MessageContent;
import be.kdg.airfreight.formatter.domain.MessageFormatter;
import org.springframework.stereotype.Component;

@Component
public class MvtEaFormatter implements MessageFormatter<FlightEstimatesChangedFact> {
    private static final int FORMAT_VERSION = 1;

    @Override
    public MessageContent format(FlightEstimatesChangedFact fact) {
        String telegram =
                MvtTelegram.lines(
                        fact.flightNumber(),
                        fact.scheduledDepartureDate(),
                        fact.aircraftRegistration(),
                        fact.arrivalAirportCode(),
                        "EA",
                        fact.estimatedArrival());
        return new MessageContent("MVT_EA", telegram, FORMAT_VERSION);
    }
}
