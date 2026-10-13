package be.kdg.airfreight.simulator.ssm;

import java.util.ArrayList;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/simulator/flight-schedules")
class SsmController {
    private final SsmPublisher publisher;

    SsmController(SsmPublisher publisher) {
        this.publisher = publisher;
    }

    @PostMapping
    ResponseEntity<Submission> submit(@RequestBody List<SsmRequest> schedules) {
        List<String> messageIds = new ArrayList<>();
        for (SsmRequest schedule : schedules) {
            messageIds.add(publisher.publish(schedule));
        }
        return ResponseEntity.accepted().body(new Submission(List.copyOf(messageIds)));
    }

    record Submission(List<String> messageIds) {}
}
