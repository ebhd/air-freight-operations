package be.kdg.airfreight.formatter.infrastructure.formatting;

import be.kdg.airfreight.formatter.domain.FlightReplacedFact;
import be.kdg.airfreight.formatter.domain.MessageContent;
import be.kdg.airfreight.formatter.domain.MessageFormatter;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

// our own json message (msg-01 allows designed messages), sent once the new flight really exists
@Component
public class FlightReplannedFormatter implements MessageFormatter<FlightReplacedFact> {
    public static final String TYPE = "FLIGHT_REPLANNED";
    private static final int FORMAT_VERSION = 1;

    private final JsonMapper jsonMapper;

    public FlightReplannedFormatter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public MessageContent format(FlightReplacedFact fact) {
        var payload =
                new FlightReplannedPayload(
                        new FlightReplannedPayload.FlightReference(
                                fact.flightNumber(),
                                fact.scheduledDepartureDate(),
                                fact.departureAirportCode()),
                        new FlightReplannedPayload.FlightReference(
                                fact.newFlightNumber(),
                                fact.newScheduledDepartureDate(),
                                fact.newDepartureAirportCode()),
                        fact.newScheduledDeparture(),
                        fact.newScheduledArrival());
        return new MessageContent(TYPE, jsonMapper.writeValueAsString(payload), FORMAT_VERSION);
    }
}
