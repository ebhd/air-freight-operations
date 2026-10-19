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
        // TODO: ask docent: should we expose both FlightCreateEvent AND Partnermessage creation
        // timestamp to the the partner? or just one

        return new MessageContent("FLM", jsonMapper.writeValueAsString(flightFact), 1);
    }
}
