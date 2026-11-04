package be.kdg.airfreight.cargo.domain.loadcheck;

// a failed hard check blocks finalizing and a failed soft check only warns
public enum CheckSeverity {
    HARD,
    SOFT
}
