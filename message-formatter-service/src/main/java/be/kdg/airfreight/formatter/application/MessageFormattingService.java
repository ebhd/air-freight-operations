package be.kdg.airfreight.formatter.application;

import be.kdg.airfreight.formatter.domain.FlightCreatedFact;
import be.kdg.airfreight.formatter.domain.FlightDepartedFact;
import be.kdg.airfreight.formatter.domain.FlightEstimatesChangedFact;
import be.kdg.airfreight.formatter.domain.FlightLandedFact;
import be.kdg.airfreight.formatter.domain.LoadFinalizedFact;
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
    private final MessageFormatter<LoadFinalizedFact> cpmFormatter;
    private final MessageFormatter<FlightDepartedFact> adFormatter;
    private final MessageFormatter<FlightLandedFact> aaFormatter;
    private final MessagePublisher publisher;

    public MessageFormattingService(
            MessageFormatter<FlightCreatedFact> flmFormatter,
            @Qualifier("mvtEdFormatter") MessageFormatter<FlightEstimatesChangedFact> edFormatter,
            @Qualifier("mvtEaFormatter") MessageFormatter<FlightEstimatesChangedFact> eaFormatter,
            MessageFormatter<LoadFinalizedFact> cpmFormatter,
            // one bean per fact type so no qualifier is needed
            MessageFormatter<FlightDepartedFact> adFormatter,
            MessageFormatter<FlightLandedFact> aaFormatter,
            MessagePublisher publisher) {
        this.flmFormatter = flmFormatter;
        this.edFormatter = edFormatter;
        this.eaFormatter = eaFormatter;
        this.cpmFormatter = cpmFormatter;
        this.adFormatter = adFormatter;
        this.aaFormatter = aaFormatter;
        this.publisher = publisher;
    }

    public void prepareFlm(UUID sourceMessageId, FlightCreatedFact fact) {
        publisher.publish(sourceMessageId, List.of(flmFormatter.format(fact)));
    }

    // the cargo message id is the source id so partner stores one cpm per finalized load
    public void prepareCpm(UUID sourceMessageId, LoadFinalizedFact fact) {
        publisher.publish(sourceMessageId, List.of(cpmFormatter.format(fact)));
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

    // the flight event id is the source id so partner stores one ad per departure
    public void prepareDeparture(UUID sourceMessageId, FlightDepartedFact fact) {
        publisher.publish(sourceMessageId, List.of(adFormatter.format(fact)));
    }

    public void prepareArrival(UUID sourceMessageId, FlightLandedFact fact) {
        publisher.publish(sourceMessageId, List.of(aaFormatter.format(fact)));
    }
}
