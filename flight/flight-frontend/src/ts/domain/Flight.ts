import type { Delay } from './Delay';

export type FlightOverview = {
    flightNumber: string;
    scheduledDepartureDate: string;
    departureAirportCode: string;
    arrivalAirportCode: string;
    status: 'PLANNED' | 'DEPARTED' | 'LANDED' | 'REPLANNED';
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

// the new schedule of a replanned flight as sent by ReplanningResponse
export type Replanning = {
    newFlightNumber: string;
    newScheduledDepartureDate: string;
    newScheduledDeparture: string;
    newScheduledArrival: string;
    replacementCreated: boolean;
    replannedAt: string;
    delayType: 'DEPARTURE' | 'ARRIVAL';
    minutes: number;
    reasonCode: string;
    description: string;
};

// the refused delay change the planner wants to replan the flight for
export type ReplanFlightRequest = {
    delayType: 'DEPARTURE' | 'ARRIVAL';
    delayId: string | null;
    minutes: number;
    reasonCode: string | null;
    description: string;
};

export type Flight = FlightOverview & {
    // null until the planner registers them
    actualDeparture: string | null;
    actualArrival: string | null;
    departureDelays: Delay[];
    arrivalDelays: Delay[];
    // null unless the flight was replanned
    replanning: Replanning | null;
    // the replanned flight this one replaces, null for a normal flight
    replaces: FlightKey | null;
};

export type FlightTimeline = {
    start: string;
    end: string;
    flights: FlightOverview[];
};
