package be.kdg.airfreight.formatter.infrastructure.formatting;

import be.kdg.airfreight.formatter.domain.FlightEstimatesChangedFact;
import be.kdg.airfreight.formatter.domain.MessageContent;
import be.kdg.airfreight.formatter.domain.MessageFormatter;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class MvtEaFormatter implements MessageFormatter<FlightEstimatesChangedFact> {
    private static final int FORMAT_VERSION = 1;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd", Locale.ROOT);
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HHmm", Locale.ROOT).withZone(ZoneOffset.UTC);

    @Override
    public MessageContent format(FlightEstimatesChangedFact fact) {
        String telegram =
                "MVT\n"
                        + fact.flightNumber()
                        + "/"
                        + DAY.format(fact.scheduledDepartureDate())
                        + "."
                        + fact.aircraftRegistration()
                        + "."
                        + fact.arrivalAirportCode()
                        + "\nEA"
                        + TIME.format(fact.estimatedArrival());
        return new MessageContent("MVT_EA", telegram, FORMAT_VERSION);
    }
}
