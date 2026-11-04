package be.kdg.airfreight.flight.application;

import be.kdg.airfreight.flight.domain.delayreason.DelayReason;
import be.kdg.airfreight.flight.domain.delayreason.DelayReasonRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DelayReasonQueryService {
    private final DelayReasonRepository delayReasons;

    public DelayReasonQueryService(DelayReasonRepository delayReasons) {
        this.delayReasons = delayReasons;
    }

    @Transactional(readOnly = true)
    public List<DelayReason> findAll() {
        return delayReasons.findAll();
    }
}
