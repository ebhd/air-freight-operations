package be.kdg.airfreight.cargo.domain.offloading;

import be.kdg.airfreight.cargo.domain.InvalidCargoValueException;
import be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition;
import java.util.List;

// positions are unique in a plan so they name the uld and read like the case examples
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
