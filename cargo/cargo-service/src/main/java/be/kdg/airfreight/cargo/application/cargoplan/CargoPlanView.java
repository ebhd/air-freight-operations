package be.kdg.airfreight.cargo.application.cargoplan;

import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlan;
import be.kdg.airfreight.cargo.domain.flight.FlightInformation;

// the flight from cargo's copy plus the cargo plan
public record CargoPlanView(FlightInformation flight, CargoPlan cargoPlan) {}
