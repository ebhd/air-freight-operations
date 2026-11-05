package be.kdg.airfreight.simulator.pages;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// a static page cannot read application.properties, so it asks here where flight and cargo are
@RestController
@RequestMapping("/api/simulator/page-urls")
class PageUrlController {
    private final PageUrls pageUrls;

    PageUrlController(
            @Value("${simulator.flight.url}") String flightUrl,
            @Value("${simulator.cargo.url}") String cargoUrl) {
        pageUrls = new PageUrls(flightUrl, cargoUrl);
    }

    @GetMapping
    PageUrls pageUrls() {
        return pageUrls;
    }

    record PageUrls(String flightUrl, String cargoUrl) {}
}
