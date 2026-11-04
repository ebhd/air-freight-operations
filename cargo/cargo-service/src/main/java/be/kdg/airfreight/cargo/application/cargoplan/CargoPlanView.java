package be.kdg.airfreight.cargo.application.cargoplan;

import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlan;
import be.kdg.airfreight.cargo.domain.flight.FlightInformation;

// flight facts from flight operations plus our own cargo plan
public record CargoPlanView(FlightInformation flight, CargoPlan cargoPlan) {}
