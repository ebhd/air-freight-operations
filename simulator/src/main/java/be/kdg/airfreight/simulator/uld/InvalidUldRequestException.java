package be.kdg.airfreight.simulator.uld;

// the simulator cannot build a message from this request
class InvalidUldRequestException extends RuntimeException {
    InvalidUldRequestException(String message) {
        super(message);
    }
}
