package be.kdg.airfreight.flight.infrastructure.messaging.ssm;

import be.kdg.airfreight.flight.application.FlightSchedulingService;
import be.kdg.airfreight.flight.application.ProcessSsmResult;
import be.kdg.airfreight.flight.domain.flight.FlightSchedule;
import be.kdg.airfreight.flight.infrastructure.messaging.RabbitConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SsmRabbitListener {
    private final FlightSchedulingService flightSchedulingService;

    public SsmRabbitListener(FlightSchedulingService flightSchedulingService) {
        this.flightSchedulingService = flightSchedulingService;
    }

    @RabbitListener(queues = RabbitConfiguration.SSM_QUEUE)
    public void receive(SsmMessage message) {
        log.info(
                "Received SSM message {} for flight {}",
                message.messageId(),
                message.flightNumber());
        log.debug("SSM message {} content: {}", message.messageId(), message);
        FlightSchedule schedule =
                new FlightSchedule(
                        message.startDate(),
                        message.endDate(),
                        message.operatingDays(),
                        message.departureTime(),
                        message.arrivalTime(),
                        message.flightNumber(),
                        message.departureAirportCode(),
                        message.arrivalAirportCode(),
                        message.aircraftRegistration());

        ProcessSsmResult result =
                flightSchedulingService.createFlights(message.messageId(), schedule);
        // after the commit, so these flights are really saved
        log.info(
                "Processed SSM message {}: flights created {}, already existing {}",
                result.messageId(),
                result.createdFlights().size(),
                result.duplicateFlightKeys().size());
    }
}
