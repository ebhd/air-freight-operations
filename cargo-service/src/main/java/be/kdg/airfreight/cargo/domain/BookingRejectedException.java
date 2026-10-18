package be.kdg.airfreight.cargo.domain;

/** A booking that may not be applied, such as one for an unknown or no longer planned flight. */
public class BookingRejectedException extends RuntimeException {
    public BookingRejectedException(String reason) {
        super(reason);
    }
}
