package be.kdg.airfreight.cargo.domain.cargoplan;

public enum CargoPlanState {
    DRAFT,
    FINALIZED,
    // the flight was replanned and the cargo moved to the new flight, final
    REPLANNED
}
