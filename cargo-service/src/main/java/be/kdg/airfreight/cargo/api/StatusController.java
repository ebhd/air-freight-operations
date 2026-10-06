package be.kdg.airfreight.cargo.api;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatusController {
    @GetMapping("/api/status")
    public Map<String, String> status() {
        return Map.of("service", "cargo-service", "status", "running");
    }
}
