package be.kdg.airfreight.partner.infrastructure.messaging;

import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

record PartnerMessageEnvelope(
        UUID messageId, Instant createdAt, int formatVersion, String type, JsonNode payload) {}
