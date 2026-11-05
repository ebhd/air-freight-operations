package be.kdg.airfreight.partner.domain;

public interface MessageFormatter<T> {
    MessageContent format(T flightFact);
}
