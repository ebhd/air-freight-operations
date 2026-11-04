package be.kdg.airfreight.flight.api.delayreason;

import be.kdg.airfreight.flight.domain.delayreason.DelayReason;

public record DelayReasonResponse(String code, String label) {
    static DelayReasonResponse from(DelayReason reason) {
        return new DelayReasonResponse(reason.code(), reason.label());
    }
}
