package be.kdg.airfreight.cargo.domain;

import java.util.ArrayList;
import java.util.List;

// destination cargo leaves through the door at rl without moving cargo for other airports
final class OffloadingCheck {

    // package private so the route tests can call it
    static List<LoadingPosition> routeToDoor(LoadingPosition position) {
        if (position.side() == LoadingPosition.Side.NONE) {
            throw new InvalidCargoValueException(
                    "Single position " + position + " has no own route to the door");
        }
        List<LoadingPosition> side = positionsOn(position.side());
        LoadingPosition rowR =
                position.side() == LoadingPosition.Side.LEFT
                        ? LoadingPosition.RL
                        : LoadingPosition.RR;
        int index = side.indexOf(position);
        int rowRIndex = side.indexOf(rowR);
        // toward the tail in front of row r and toward the nose behind it
        int step = index < rowRIndex ? 1 : -1;
        List<LoadingPosition> route = new ArrayList<>();
        while (index != rowRIndex) {
            index += step;
            route.add(side.get(index));
        }
        if (position.side() == LoadingPosition.Side.RIGHT) {
            // right side cargo crosses from rr to the door at rl
            route.add(LoadingPosition.RL);
        }
        return route;
    }

    // the positions of one side from nose to tail
    private static List<LoadingPosition> positionsOn(LoadingPosition.Side side) {
        List<LoadingPosition> positions = new ArrayList<>();
        for (LoadingPosition position : LoadingPosition.values()) {
            if (position.side() == side) {
                positions.add(position);
            }
        }
        return positions;
    }
}
