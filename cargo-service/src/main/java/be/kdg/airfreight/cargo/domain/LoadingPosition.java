package be.kdg.airfreight.cargo.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

// main deck of the boeing 747-400 freighter from nose to tail with the door left on row r
public enum LoadingPosition {
    A1('A', Side.NONE),
    A2('A', Side.NONE),
    B1('B', Side.NONE),
    CL('C', Side.LEFT),
    CR('C', Side.RIGHT),
    DL('D', Side.LEFT),
    DR('D', Side.RIGHT),
    EL('E', Side.LEFT),
    ER('E', Side.RIGHT),
    FL('F', Side.LEFT),
    FR('F', Side.RIGHT),
    GL('G', Side.LEFT),
    GR('G', Side.RIGHT),
    HL('H', Side.LEFT),
    HR('H', Side.RIGHT),
    JL('J', Side.LEFT),
    JR('J', Side.RIGHT),
    KL('K', Side.LEFT),
    KR('K', Side.RIGHT),
    LL('L', Side.LEFT),
    // row l right side and not the door
    LR('L', Side.RIGHT),
    ML('M', Side.LEFT),
    MR('M', Side.RIGHT),
    PL('P', Side.LEFT),
    PR('P', Side.RIGHT),
    // row r left side next to the door
    RL('R', Side.LEFT),
    RR('R', Side.RIGHT),
    SL('S', Side.LEFT),
    SR('S', Side.RIGHT),
    TL('T', Side.LEFT),
    TR('T', Side.RIGHT),
    UL('U', Side.LEFT),
    UR('U', Side.RIGHT),
    X('X', Side.NONE);

    // a1 a2 b1 and x are single positions without a side
    public enum Side {
        LEFT,
        RIGHT,
        NONE
    }

    private final char row;
    private final Side side;

    LoadingPosition(char row, Side side) {
        this.row = row;
        this.side = side;
    }

    public char row() {
        return row;
    }

    public Side side() {
        return side;
    }

    public static LoadingPosition fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new InvalidCargoValueException("Loading position is required");
        }
        try {
            return valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            // valueOf throws this for an unknown name
            throw new InvalidCargoValueException(
                    "Unknown loading position " + code.trim() + ", valid positions are " + codes());
        }
    }

    private static String codes() {
        return Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "));
    }
}
