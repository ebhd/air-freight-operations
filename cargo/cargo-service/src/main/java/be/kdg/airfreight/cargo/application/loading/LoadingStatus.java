package be.kdg.airfreight.cargo.application.loading;

import be.kdg.airfreight.cargo.domain.loading.LoadingContext;
import be.kdg.airfreight.cargo.domain.loading.LoadingRefusal;

// refusal is null when loading is allowed
public record LoadingStatus(LoadingContext context, boolean finalized, LoadingRefusal refusal) {
    public boolean loadingAllowed() {
        return refusal == null;
    }
}
