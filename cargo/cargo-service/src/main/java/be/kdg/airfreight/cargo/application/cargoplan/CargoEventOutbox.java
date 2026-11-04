package be.kdg.airfreight.cargo.application.cargoplan;

import be.kdg.airfreight.cargo.domain.cargoplan.LoadFinalized;

// stores the event in the transaction of the caller, sending happens after the commit
public interface CargoEventOutbox {
    void add(LoadFinalized event);
}
