package be.kdg.airfreight.simulator.uld;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class UldControllerTest {
    private static final String RECORD =
            """
            {"flightNumber": "HJ221", "scheduledDepartureDate": "2026-10-26",
             "departureAirportCode": "BRU", "uldCode": "AKE12345KL",
             "weight": {"value": 1250.5, "unit": "KG"}, "destination": "AMS"}
            """;

    // real controller and publisher with only rabbitmq faked
    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final MockMvc mvc =
            MockMvcBuilders.standaloneSetup(new UldController(new UldPublisher(rabbit))).build();

    @Test
    void publishesOneMessagePerRecord() throws Exception {
        ResultActions response =
                submit("[" + RECORD + "," + RECORD.replace("AKE12345KL", "AKE67890KL") + "]")
                        .andExpect(status().isAccepted());

        List<UldMessage> sent = sentMessages(2);
        assertThat(sent)
                .extracting(UldMessage::uldCode)
                .containsExactly("AKE12345KL", "AKE67890KL");
        response.andExpect(jsonPath("$.messageIds.length()").value(2))
                .andExpect(jsonPath("$.messageIds[0]").value(sent.get(0).messageId()))
                .andExpect(jsonPath("$.messageIds[1]").value(sent.get(1).messageId()));
    }

    @Test
    void publishesInvalidBusinessDataUnchanged() throws Exception {
        String invalid =
                RECORD.replace("1250.5", "-5").replace("\"KG\"", "\"TON\"").replace("BRU", "BRUX");

        submit("[" + invalid + "]").andExpect(status().isAccepted());

        UldMessage sent = sentMessages(1).getFirst();
        assertThat(sent.weight()).isEqualTo(new UldWeight(new BigDecimal("-5"), "TON"));
        assertThat(sent.departureAirportCode()).isEqualTo("BRUX");
    }

    @Test
    void refusesEmptyArray() throws Exception {
        submit("[]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Send at least one ULD record"));
        verifyNoInteractions(rabbit);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"flightNumber\": \"HJ221\", \"uldCode\": \"AKE67890KL\"}", "null"})
    void refusesMissingDateWithoutPublishingAnything(String secondRecord) throws Exception {
        submit("[" + RECORD + "," + secondRecord + "]")
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "Record 2: scheduledDepartureDate is required"
                                                + " (yyyy-MM-dd, UTC)"));
        // record 1 was valid but is not sent either
        verifyNoInteractions(rabbit);
    }

    @Test
    void refusesInvalidDate() throws Exception {
        submit("[" + RECORD.replace("2026-10-26", "2026-02-30") + "]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("FEBRUARY 30")));
        verifyNoInteractions(rabbit);
    }

    @ParameterizedTest
    @ValueSource(strings = {RECORD, "[{"})
    void refusesMalformedJsonAndSingleObject(String body) throws Exception {
        submit(body)
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.detail")
                                .value(startsWith("Body must be a JSON array of ULD records")));
        verifyNoInteractions(rabbit);
    }

    @Test
    void rabbitMqDownIsServiceUnavailable() throws Exception {
        doNothing()
                .doThrow(new AmqpConnectException(new ConnectException("Connection refused")))
                .when(rabbit)
                .convertAndSend(
                        eq(""),
                        eq("cargo.uld"),
                        any(Object.class),
                        any(MessagePostProcessor.class));

        submit("[" + RECORD + "," + RECORD + "," + RECORD + "]")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value(startsWith("RabbitMQ is unavailable")));
        // the first record went out the second failed and the third was never tried
        sentMessages(2);
    }

    private ResultActions submit(String body) throws Exception {
        return mvc.perform(
                post("/api/simulator/uld-bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    private List<UldMessage> sentMessages(int count) {
        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(rabbit, times(count))
                .convertAndSend(
                        eq(""), eq("cargo.uld"), sent.capture(), any(MessagePostProcessor.class));
        return sent.getAllValues().stream().map(UldMessage.class::cast).toList();
    }
}
