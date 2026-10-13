package be.kdg.airfreight.simulator.ssm;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

public record SsmMessage(
        String messageId,
        LocalDate startDate,
        LocalDate endDate,
        Set<DayOfWeek> operatingDays,
        LocalTime departureTime,
        LocalTime arrivalTime,
        String flightNumber,
        String departureAirportCode,
        String arrivalAirportCode,
        String aircraftRegistration) {}
