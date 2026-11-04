package be.kdg.airfreight.simulator.replanning;

import be.kdg.airfreight.simulator.configuration.MessagingConfiguration;
import be.kdg.airfreight.simulator.ssm.SsmMessage;
import be.kdg.airfreight.simulator.ssm.SsmPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

// plays the external planning system: it answers a replanning with a normal ssm
@Slf4j
@Component
class FlightReplannedListener {
    private final SsmPublisher ssmPublisher;

    FlightReplannedListener(SsmPublisher ssmPublisher) {
        this.ssmPublisher = ssmPublisher;
    }

    @RabbitListener(queues = MessagingConfiguration.PLANNING_REPLANNED_QUEUE)
    void receive(FlightReplannedMessage event) {
        log.info("Received FlightReplanned message {}", event.messageId());
        requireValid(event);
        SsmMessage ssm = ReplanningSsmMapper.toSsm(event);
        ssmPublisher.publish(ssm);
        log.info(
                "Published SSM {} for replanned flight {}/{}/{}: {} on {} {}-{} UTC",
                ssm.messageId(),
                event.flightNumber(),
                event.scheduledDepartureDate(),
                event.departureAirportCode(),
                ssm.flightNumber(),
                ssm.startDate(),
                ssm.departureTime(),
                ssm.arrivalTime());
    }

    // an invalid event goes to the dead letter queue at once, retrying would not help
    private static void requireValid(FlightReplannedMessage event) {
        if (event.version() == null || event.version() != FlightReplannedMessage.VERSION) {
            throw new AmqpRejectAndDontRequeueException(
                    "unsupported FlightReplanned version: " + event.version());
        }
        if (event.messageId() == null || event.messageId().isBlank()) {
            throw new AmqpRejectAndDontRequeueException("FlightReplanned has no message ID");
        }
        if (event.newFlightNumber() == null
                || event.newScheduledDeparture() == null
                || event.newScheduledArrival() == null
                || event.departureAirportCode() == null
                || event.arrivalAirportCode() == null
                || event.aircraftRegistration() == null) {
            throw new AmqpRejectAndDontRequeueException(
                    "FlightReplanned " + event.messageId() + " misses a field for the new flight");
        }
    }
}
