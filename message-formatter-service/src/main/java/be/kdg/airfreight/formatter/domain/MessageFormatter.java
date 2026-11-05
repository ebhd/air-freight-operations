package be.kdg.airfreight.formatter.domain;

public interface MessageFormatter<T> {
    MessageContent format(T flightFact);
}
