package be.kdg.airfreight.flight.infrastructure.messaging;

import be.kdg.airfreight.flight.application.FlightSchedulingService;
import be.kdg.airfreight.flight.domain.FlightSchedule;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class SsmRabbitListener {
    private final FlightSchedulingService flightSchedulingService;

    public SsmRabbitListener(FlightSchedulingService flightSchedulingService) {
        this.flightSchedulingService = flightSchedulingService;
    }

    @RabbitListener(queues = RabbitConfiguration.SSM_QUEUE)
    public void receive(SsmMessage message) {
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
