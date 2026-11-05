package be.kdg.airfreight.cargo.domain.loadcheck;

import be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition;
import be.kdg.airfreight.cargo.domain.exception.InvalidCargoValueException;
import java.util.List;

// positions are unique in a plan so they also name the uld
public record OffloadingResult(
        List<LoadingPosition> unloadingOrder, List<LoadingPosition> blockedPositions) {
    public OffloadingResult {
        if (unloadingOrder == null) {
            throw new InvalidCargoValueException("Unloading order is required");
        }
        if (blockedPositions == null) {
            throw new InvalidCargoValueException("Blocked positions are required");
        }
        unloadingOrder = List.copyOf(unloadingOrder);
        blockedPositions = List.copyOf(blockedPositions);
    }

    public boolean passed() {
        return blockedPositions.isEmpty();
    }
}
