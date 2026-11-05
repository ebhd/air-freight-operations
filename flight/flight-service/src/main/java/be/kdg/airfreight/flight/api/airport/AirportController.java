package be.kdg.airfreight.flight.api.airport;

import be.kdg.airfreight.flight.application.AirportQueryService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/airports")
public class AirportController {
    private final AirportQueryService airports;

    public AirportController(AirportQueryService airports) {
        this.airports = airports;
    }

    @GetMapping
    public List<AirportResponse> findAll() {
        return airports.findAll().stream().map(AirportResponse::from).toList();
    }
}
