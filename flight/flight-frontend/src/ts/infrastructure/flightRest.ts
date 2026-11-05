import type { AddDelayRequest, UpdateDelayRequest } from '../domain/Delay';
import type { Flight, FlightKey, FlightOverview } from '../domain/Flight';
import { request } from './restUtils';

export async function getFlights(date: string): Promise<FlightOverview[]> {
    const query = new URLSearchParams({ date });
    const response = await request(`/api/flights?${query}`);
    return response.json();
}

export async function getFlight(key: FlightKey): Promise<Flight> {
    const response = await request(flightPath(key));
    return response.json();
}

export async function addDelay(key: FlightKey, delay: AddDelayRequest): Promise<Flight> {
    const response = await request(`${flightPath(key)}/delays`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(delay),
    });
    return response.json();
}

export async function updateDelay(key: FlightKey, delayId: string, delay: UpdateDelayRequest): Promise<void> {
    await request(`${flightPath(key)}/delays/${encodeURIComponent(delayId)}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(delay),
    });
}

export async function removeDelay(key: FlightKey, delayId: string): Promise<void> {
    await request(`${flightPath(key)}/delays/${encodeURIComponent(delayId)}`, { method: 'DELETE' });
}

function flightPath(key: FlightKey): string {
    return `/api/flights/${encodeURIComponent(key.flightNumber)}/${encodeURIComponent(key.scheduledDepartureDate)}/${encodeURIComponent(key.departureAirportCode)}`;
}
