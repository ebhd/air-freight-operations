package be.kdg.airfreight.cargo.api.loadingposition;

import be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoadingPositionController {
    // the page fills its dropdown from here so the list exists only in the enum
    @GetMapping("/api/loading-positions")
    public List<LoadingPositionResponse> findLoadingPositions() {
        return Arrays.stream(LoadingPosition.values()).map(LoadingPositionResponse::from).toList();
    }
}
