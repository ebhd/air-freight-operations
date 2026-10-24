package be.kdg.airfreight.cargo.application;

import be.kdg.airfreight.cargo.domain.LoadingContext;
import be.kdg.airfreight.cargo.domain.LoadingRefusal;

// refusal is null when loading is allowed
public record LoadingStatus(LoadingContext context, boolean finalized, LoadingRefusal refusal) {
    public boolean loadingAllowed() {
        return refusal == null;
    }
}
