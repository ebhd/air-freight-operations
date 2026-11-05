import type { AddDelayRequest, UpdateDelayRequest } from '../domain/Delay';
import type { Flight, FlightKey, FlightOverview, FlightTimeline } from '../domain/Flight';
import { request } from './restUtils';

export async function getFlights(date: string): Promise<FlightOverview[]> {
    const query = new URLSearchParams({ date });
    const response = await request(`/api/flights?${query}`);
    return response.json();
}

export async function getFlightTimeline(date: string): Promise<FlightTimeline> {
    const query = new URLSearchParams({ date });
    const response = await request(`/api/flights/timeline?${query}`);
    return response.json();
}

export async function getFlight(key: FlightKey): Promise<Flight> {
    const response = await request(flightPath(key));
    return response.json();
}

export async function addDepartureDelay(key: FlightKey, delay: AddDelayRequest): Promise<Flight> {
    const response = await request(`${flightPath(key)}/departure-delays`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(delay),
    });
    return response.json();
}

export async function updateDepartureDelay(key: FlightKey, delayId: string, delay: UpdateDelayRequest): Promise<void> {
    await request(`${flightPath(key)}/departure-delays/${encodeURIComponent(delayId)}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(delay),
    });
}

export async function removeDepartureDelay(key: FlightKey, delayId: string): Promise<void> {
    await request(`${flightPath(key)}/departure-delays/${encodeURIComponent(delayId)}`, { method: 'DELETE' });
}

export async function addArrivalDelay(key: FlightKey, delay: AddDelayRequest): Promise<Flight> {
    const response = await request(`${flightPath(key)}/arrival-delays`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(delay),
    });
    return response.json();
}

export async function updateArrivalDelay(key: FlightKey, delayId: string, delay: UpdateDelayRequest): Promise<void> {
    await request(`${flightPath(key)}/arrival-delays/${encodeURIComponent(delayId)}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(delay),
    });
}

export async function removeArrivalDelay(key: FlightKey, delayId: string): Promise<void> {
    await request(`${flightPath(key)}/arrival-delays/${encodeURIComponent(delayId)}`, { method: 'DELETE' });
}

function flightPath(key: FlightKey): string {
    return `/api/flights/${encodeURIComponent(key.flightNumber)}/${encodeURIComponent(key.scheduledDepartureDate)}/${encodeURIComponent(key.departureAirportCode)}`;
}
