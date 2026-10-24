package be.kdg.airfreight.simulator.uld;

// the provisional uld message q01 that cargo reads from cargo.uld
public record UldMessage(
        String messageId,
        int version,
        String flightNumber,
        int scheduledDepartureDay,
        String departureAirportCode,
        String uldCode,
        UldWeight weight,
        String destination) {
    static final int VERSION = 1;

    // the uld message only carries the day of month and cargo rebuilds the date
    static UldMessage from(String messageId, UldRequest request) {
        return new UldMessage(
                messageId,
                VERSION,
                request.flightNumber(),
                request.scheduledDepartureDate().getDayOfMonth(),
                request.departureAirportCode(),
                request.uldCode(),
                request.weight(),
                request.destination());
    }
}
