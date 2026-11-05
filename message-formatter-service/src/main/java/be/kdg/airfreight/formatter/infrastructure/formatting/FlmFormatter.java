package be.kdg.airfreight.formatter.infrastructure.formatting;

import be.kdg.airfreight.formatter.domain.FlightCreatedFact;
import be.kdg.airfreight.formatter.domain.MessageContent;
import be.kdg.airfreight.formatter.domain.MessageFormatter;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class FlmFormatter implements MessageFormatter<FlightCreatedFact> {
    private static final int FORMAT_VERSION = 1;

    private final JsonMapper jsonMapper;

    public FlmFormatter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public MessageContent format(FlightCreatedFact flightFact) {
        var payload =
                new FlmPayload(
                        flightFact.flightNumber(),
                        flightFact.scheduledDepartureDate(),
                        flightFact.departureAirportCode(),
                        flightFact.arrivalAirportCode(),
                        flightFact.scheduledDeparture(),
                        flightFact.scheduledArrival());

        return new MessageContent("FLM", jsonMapper.writeValueAsString(payload), FORMAT_VERSION);
    }
}
