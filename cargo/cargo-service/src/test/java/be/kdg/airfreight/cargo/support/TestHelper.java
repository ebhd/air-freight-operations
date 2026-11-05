package be.kdg.airfreight.cargo.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlan;
import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlanRepository;
import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import be.kdg.airfreight.cargo.domain.flight.FlightInformation;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.domain.flight.FlightStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@TestConfiguration(proxyBeanMethods = false)
public class TestHelper {
    @Autowired private CargoPlanRepository plans;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private JdbcClient jdbc;

    public static FlightInformation plannedFlight(FlightKey key) {
        Instant std = key.scheduledDepartureDate().atTime(8, 0).toInstant(ZoneOffset.UTC);
        return new FlightInformation(
                key, new AirportCode("ATH"), FlightStatus.PLANNED, std, std.plusSeconds(7200), std);
    }

    public void save(CargoPlan plan) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> plans.save(plan));
    }

    public CargoPlan find(FlightKey key) {
        return plans.findByFlightKey(key).orElseThrow();
    }

    public void delete(FlightKey key) {
        new TransactionTemplate(transactions)
                .executeWithoutResult(status -> plans.deleteByFlightKey(key));
    }

    public long outboxCount() {
        return jdbc.sql("SELECT COUNT(*) FROM outbox_messages").query(Long.class).single();
    }

    public void cleanUp() {
        jdbc.sql("DELETE FROM outbox_messages").update();
        jdbc.sql("DELETE FROM uld_bookings").update();
        jdbc.sql("DELETE FROM cargo_plans").update();
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
