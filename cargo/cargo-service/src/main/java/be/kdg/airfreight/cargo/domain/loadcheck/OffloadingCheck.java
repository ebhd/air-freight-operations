package be.kdg.airfreight.cargo.domain.loadcheck;

import be.kdg.airfreight.cargo.domain.InvalidCargoValueException;
import be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition;
import be.kdg.airfreight.cargo.domain.cargoplan.UldBooking;
import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// destination cargo leaves through the door at rl without moving cargo for other airports
public final class OffloadingCheck implements LoadCheck {
    static final String NAME = "Offloading";

    @Override
    public LoadCheckResult evaluate(List<UldBooking> bookings, AirportCode arrivalAirport) {
        OffloadingResult result = simulate(bookings, arrivalAirport);
        if (result.passed()) {
            return new LoadCheckResult(
                    NAME,
                    CheckSeverity.HARD,
                    true,
                    "All cargo for " + arrivalAirport + " can leave without moving other cargo");
        }
        String blocked =
                result.blockedPositions().stream()
                        .map(LoadingPosition::name)
                        .collect(Collectors.joining(", "));
        return new LoadCheckResult(
                NAME,
                CheckSeverity.HARD,
                false,
                "Cargo for "
                        + arrivalAirport
                        + " at "
                        + blocked
                        + " cannot leave without moving cargo for other airports");
    }

    // unloads on a copy of the positions so the plan itself never changes
    public OffloadingResult simulate(List<UldBooking> bookings, AirportCode arrivalAirport) {
        if (bookings == null) {
            throw new InvalidCargoValueException("Bookings are required");
        }
        if (arrivalAirport == null) {
            throw new InvalidCargoValueException("Arrival airport is required");
        }
        Set<LoadingPosition> occupied = new HashSet<>();
        Set<LoadingPosition> toUnload = new HashSet<>();
        for (UldBooking booking : bookings) {
            Optional<LoadingPosition> position = booking.getPosition();
            // hard checks look at positioned cargo only
            if (position.isPresent()) {
                occupied.add(position.get());
                if (booking.getDestination().equals(arrivalAirport)) {
                    toUnload.add(position.get());
                }
            }
        }
        List<LoadingPosition> unloadingOrder = new ArrayList<>();
        boolean unloadedAny = true;
        // a pass that unloads nothing means the rest is stuck
        while (unloadedAny) {
            unloadedAny = false;
            for (LoadingPosition position : LoadingPosition.values()) {
                if (toUnload.contains(position) && canLeave(position, occupied)) {
                    toUnload.remove(position);
                    occupied.remove(position);
                    unloadingOrder.add(position);
                    unloadedAny = true;
                }
            }
        }
        List<LoadingPosition> blockedPositions = new ArrayList<>();
        for (LoadingPosition position : LoadingPosition.values()) {
            if (toUnload.contains(position)) {
                blockedPositions.add(position);
            }
        }
        return new OffloadingResult(unloadingOrder, blockedPositions);
    }

    private static boolean canLeave(LoadingPosition position, Set<LoadingPosition> occupied) {
        if (position == LoadingPosition.X) {
            return canManeuver(LoadingPosition.UL, LoadingPosition.UR, occupied);
        }
        if (position.side() == LoadingPosition.Side.NONE) {
            // a1 a2 and b1 do not block each other because the case sets no such rule
            return canManeuver(LoadingPosition.CL, LoadingPosition.CR, occupied);
        }
        return isClear(routeToDoor(position), occupied);
    }

    // both maneuvering positions must be empty and then one side route is enough
    private static boolean canManeuver(
            LoadingPosition left, LoadingPosition right, Set<LoadingPosition> occupied) {
        if (occupied.contains(left) || occupied.contains(right)) {
            return false;
        }
        return isClear(routeToDoor(left), occupied) || isClear(routeToDoor(right), occupied);
    }

    // the route must be empty and not only free of other airports so the order is possible
    private static boolean isClear(List<LoadingPosition> route, Set<LoadingPosition> occupied) {
        for (LoadingPosition passed : route) {
            if (occupied.contains(passed)) {
                return false;
            }
        }
        return true;
    }

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
