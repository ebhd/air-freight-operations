package be.kdg.airfreight.cargo.application;

import be.kdg.airfreight.cargo.domain.CargoPlan;
import be.kdg.airfreight.cargo.domain.FlightInformation;

// flight facts from flight operations plus our own cargo plan
public record CargoPlanView(FlightInformation flight, CargoPlan cargoPlan) {}
