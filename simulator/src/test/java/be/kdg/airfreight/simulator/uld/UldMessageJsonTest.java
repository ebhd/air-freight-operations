package be.kdg.airfreight.simulator.uld;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import tools.jackson.databind.json.JsonMapper;

class UldMessageJsonTest {
    // the sample from the readme so a renamed field breaks this test before it breaks cargo
    private static final String README_SAMPLE =
            """
            {
              "messageId": "uld-0001",
              "version": 1,
              "flightNumber": "HJ221",
              "scheduledDepartureDay": 19,
              "departureAirportCode": "BRU",
              "uldCode": "AKE12345KL",
              "weight": { "value": 1250.5, "unit": "KG" },
              "destination": "AMS"
            }
            """;

    @Test
    void matchesProvisionalContract() throws Exception {
        UldRequest request =
                new UldRequest(
                        null,
                        "HJ221",
                        LocalDate.of(2026, 10, 19),
                        "BRU",
                        "AKE12345KL",
                        new UldWeight(new BigDecimal("1250.5"), "KG"),
                        "AMS");

        String json =
                JsonMapper.builder()
                        .build()
                        .writeValueAsString(UldMessage.from("uld-0001", request));

        JSONAssert.assertEquals(README_SAMPLE, json, JSONCompareMode.STRICT);
    }
}
