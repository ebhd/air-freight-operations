package be.kdg.airfreight.cargo.api.loadingposition;

import be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition;

public record LoadingPositionResponse(String code, String row, String side) {
    static LoadingPositionResponse from(LoadingPosition position) {
        return new LoadingPositionResponse(
                position.name(), String.valueOf(position.row()), position.side().name());
    }
}
