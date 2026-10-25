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

export type FlightKey = Pick<FlightOverview, 'flightNumber' | 'scheduledDepartureDate' | 'departureAirportCode'>;

export type Flight = FlightOverview & { delays: Delay[] };
