package be.kdg.airfreight.cargo.domain.cargoplan;

// unknown or no longer planned flight so reject instead of retrying
public class BookingRejectedException extends RuntimeException {
    public BookingRejectedException(String reason) {
        super(reason);
    }
}
