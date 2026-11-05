package be.kdg.airfreight.formatter.infrastructure.formatting;

import be.kdg.airfreight.formatter.domain.LoadFinalizedFact;
import be.kdg.airfreight.formatter.domain.MessageContent;
import be.kdg.airfreight.formatter.domain.MessageFormatter;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class CpmFormatter implements MessageFormatter<LoadFinalizedFact> {
    private static final int FORMAT_VERSION = 1;

    private final JsonMapper jsonMapper;

    public CpmFormatter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public MessageContent format(LoadFinalizedFact fact) {
        var payload =
                new CpmPayload(
                        fact.flightNumber(),
                        fact.scheduledDepartureDate(),
                        fact.departureAirportCode(),
                        fact.ulds().stream()
                                .map(
                                        uld ->
                                                new CpmPayload.Uld(
                                                        uld.uldCode(),
                                                        uld.position(),
                                                        new CpmPayload.Weight(
                                                                uld.weightValue(),
                                                                uld.weightUnit()),
                                                        uld.destinationAirportCode()))
                                .toList());
        return new MessageContent("CPM", jsonMapper.writeValueAsString(payload), FORMAT_VERSION);
    }
}
