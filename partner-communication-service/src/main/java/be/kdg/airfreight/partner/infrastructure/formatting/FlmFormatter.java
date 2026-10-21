package be.kdg.airfreight.partner.infrastructure.formatting;

import be.kdg.airfreight.partner.domain.FlightCreatedFact;
import be.kdg.airfreight.partner.domain.MessageContent;
import be.kdg.airfreight.partner.domain.MessageFormatter;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class FlmFormatter implements MessageFormatter<FlightCreatedFact> {
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

        return new MessageContent("FLM", jsonMapper.writeValueAsString(payload), 1);
    }
}
