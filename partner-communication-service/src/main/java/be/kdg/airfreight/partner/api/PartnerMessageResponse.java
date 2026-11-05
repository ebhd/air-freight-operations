package be.kdg.airfreight.partner.api;

import be.kdg.airfreight.partner.domain.MessageContent;
import be.kdg.airfreight.partner.domain.PartnerMessage;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// what the messages page shows, the time is a utc instant
public record PartnerMessageResponse(
        UUID id,
        UUID sourceMessageId,
        Instant createdAt,
        String type,
        int formatVersion,
        String payload,
        String state,
        // null when the payload does not name a flight
        String flightNumber) {
    static PartnerMessageResponse from(PartnerMessage message, JsonMapper jsonMapper) {
        MessageContent content = message.content();
        return new PartnerMessageResponse(
                message.id().value(),
                message.sourceMessageId(),
                message.createdAtUTC(),
                content.type(),
                content.formatVersion(),
                content.payload(),
                message.state().name(),
                flightNumber(content, jsonMapper));
    }

    private static String flightNumber(MessageContent content, JsonMapper jsonMapper) {
        if (content.type().startsWith("MVT")) {
            return flightNumberFromMvt(content.payload());
        }
        return flightNumberFromJson(content.payload(), jsonMapper);
    }

    // the second line of an mvt looks like HJ221/09.SXBAA.BRU
    private static String flightNumberFromMvt(String payload) {
        String[] lines = payload.split("\\R");
        if (lines.length < 2 || lines[1].indexOf('/') < 1) {
            return null;
        }
        return lines[1].substring(0, lines[1].indexOf('/'));
    }

    // a replanned flight has no flightNumber of its own, so this takes the new flight's
    private static String flightNumberFromJson(String payload, JsonMapper jsonMapper) {
        try {
            JsonNode root = jsonMapper.readTree(payload);
            String newFlightNumber = root.at("/newFlight/flightNumber").stringValue(null);
            return root.path("flightNumber").stringValue(newFlightNumber);
        } catch (JacksonException exception) {
            // only for display, so a payload we cannot read must not break the whole list
            return null;
        }
    }
}
