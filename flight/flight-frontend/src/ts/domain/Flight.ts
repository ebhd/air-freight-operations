import type { Delay } from './Delay';

export type FlightOverview = {
    flightNumber: string;
    scheduledDepartureDate: string;
    departureAirportCode: string;
    arrivalAirportCode: string;
    status: 'PLANNED' | 'DEPARTED' | 'LANDED';
    scheduledDeparture: string;
    scheduledArrival: string;
    estimatedDeparture: string;
    estimatedArrival: string;
};

export type FlightKey = {
    flightNumber: string;
    scheduledDepartureDate: string;
    departureAirportCode: string;
};

export type Flight = FlightOverview & {
    departureDelays: Delay[];
    arrivalDelays: Delay[];
};
