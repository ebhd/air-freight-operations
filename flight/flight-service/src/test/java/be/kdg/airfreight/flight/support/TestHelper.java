package be.kdg.airfreight.flight.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import be.kdg.airfreight.flight.domain.flight.Flight;
import be.kdg.airfreight.flight.domain.flight.FlightKey;
import be.kdg.airfreight.flight.domain.flight.FlightRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@TestConfiguration
public class TestHelper {
    @Autowired private FlightRepository flights;
    @Autowired private JdbcClient jdbc;

    public void save(Flight flight) {
        flights.save(flight);
    }

    @Transactional(readOnly = true)
    public Flight find(FlightKey key) {
        return flights.findById(key).orElseThrow();
    }

    public void cleanUp() {
        jdbc.sql("DELETE FROM outbox_messages").update();
        jdbc.sql("DELETE FROM departure_delays").update();
        jdbc.sql("DELETE FROM arrival_delays").update();
        jdbc.sql("DELETE FROM flights").update();
        jdbc.sql("DELETE FROM delay_reasons").update();
        jdbc.sql(
                        "INSERT INTO delay_reasons (code, label) VALUES ('71', 'Weather at departure station')")
                .update();
    }

    public void prepare(Clock clock, RabbitTemplate rabbit, Instant now) {
        when(clock.instant()).thenReturn(now);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        doAnswer(
                        call -> {
                            CorrelationData correlation = call.getArgument(3);
                            correlation
                                    .getFuture()
                                    .complete(new CorrelationData.Confirm(true, null));
                            return null;
                        })
                .when(rabbit)
                .send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }
}
