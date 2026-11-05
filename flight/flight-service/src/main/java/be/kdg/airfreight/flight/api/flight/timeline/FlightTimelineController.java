package be.kdg.airfreight.flight.api.flight.timeline;

import be.kdg.airfreight.flight.api.flight.FlightOverviewResponse;
import be.kdg.airfreight.flight.application.flight.FlightQueryService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FlightTimelineController {
    private final FlightQueryService flightQueryService;

    public FlightTimelineController(FlightQueryService flightQueryService) {
        this.flightQueryService = flightQueryService;
    }

    @GetMapping("/api/flights/timeline")
    public TimelineResponse findTimeline(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        var timeline = flightQueryService.findTimelineOn(date);
        var flights = timeline.flights().stream().map(FlightOverviewResponse::from).toList();
        return new TimelineResponse(timeline.start(), timeline.end(), flights);
    }
}
