package be.kdg.airfreight.flight.domain.delayreason;

import java.util.List;
import java.util.Optional;

public interface DelayReasonRepository {
    Optional<DelayReason> findByCode(String code);

    List<DelayReason> findAll();
}
