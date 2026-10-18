package be.kdg.airfreight.flight.infrastructure.messaging;

import be.kdg.airfreight.flight.application.FlightSchedulingService;
import be.kdg.airfreight.flight.domain.FlightSchedule;
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
        log.info("Received SSM message: {}", message);
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

        flightSchedulingService.createFlights(message.messageId(), schedule);
    }
}
