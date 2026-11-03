package be.kdg.airfreight.formatter.application;

import be.kdg.airfreight.formatter.domain.FlightCreatedFact;
import be.kdg.airfreight.formatter.domain.FlightEstimatesChangedFact;
import be.kdg.airfreight.formatter.domain.MessageContent;
import be.kdg.airfreight.formatter.domain.MessageFormatter;
import be.kdg.airfreight.formatter.domain.MessagePublisher;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class MessageFormattingService {
    private final MessageFormatter<FlightCreatedFact> flmFormatter;
    private final MessageFormatter<FlightEstimatesChangedFact> edFormatter;
    private final MessageFormatter<FlightEstimatesChangedFact> eaFormatter;
    private final MessagePublisher publisher;

    public MessageFormattingService(
            MessageFormatter<FlightCreatedFact> flmFormatter,
            @Qualifier("mvtEdFormatter") MessageFormatter<FlightEstimatesChangedFact> edFormatter,
            @Qualifier("mvtEaFormatter") MessageFormatter<FlightEstimatesChangedFact> eaFormatter,
            MessagePublisher publisher) {
        this.flmFormatter = flmFormatter;
        this.edFormatter = edFormatter;
        this.eaFormatter = eaFormatter;
        this.publisher = publisher;
    }

    public void prepareFlm(UUID sourceMessageId, FlightCreatedFact fact) {
        publisher.publish(sourceMessageId, List.of(flmFormatter.format(fact)));
    }

    public void prepareEstimates(UUID sourceMessageId, FlightEstimatesChangedFact fact) {
        var contents = new ArrayList<MessageContent>();
        if (fact.departureChanged()) {
            contents.add(edFormatter.format(fact));
        }
        if (fact.departureChanged() || fact.arrivalChanged()) {
            contents.add(eaFormatter.format(fact));
        }
        if (!contents.isEmpty()) {
            publisher.publish(sourceMessageId, contents);
        }
    }
}
