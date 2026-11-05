package be.kdg.airfreight.formatter.infrastructure.formatting;

import be.kdg.airfreight.formatter.domain.FlightDepartedFact;
import be.kdg.airfreight.formatter.domain.MessageContent;
import be.kdg.airfreight.formatter.domain.MessageFormatter;
import org.springframework.stereotype.Component;

@Component
public class MvtAdFormatter implements MessageFormatter<FlightDepartedFact> {
    private static final int FORMAT_VERSION = 1;

    @Override
    public MessageContent format(FlightDepartedFact fact) {
        String telegram =
                MvtTelegram.lines(
                        fact.flightNumber(),
                        fact.scheduledDepartureDate(),
                        fact.aircraftRegistration(),
                        fact.departureAirportCode(),
                        "AD",
                        fact.actualDeparture());
        return new MessageContent("MVT_AD", telegram, FORMAT_VERSION);
    }
}
