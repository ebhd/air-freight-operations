package be.kdg.airfreight.flight.api.delayreason;

import be.kdg.airfreight.flight.application.DelayReasonQueryService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/delay-reasons")
public class DelayReasonController {
    private final DelayReasonQueryService delayReasons;

    public DelayReasonController(DelayReasonQueryService delayReasons) {
        this.delayReasons = delayReasons;
    }

    @GetMapping
    public List<DelayReasonResponse> findAll() {
        return delayReasons.findAll().stream().map(DelayReasonResponse::from).toList();
    }
}
