import type { AddDelayRequest, UpdateDelayRequest } from '../domain/Delay';
import type { Flight, FlightKey, FlightOverview, FlightTimeline, ReplanFlightRequest } from '../domain/Flight';
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

// the times are utc instants, the page converts the brussels input first
export async function recordDeparture(key: FlightKey, actualDeparture: string): Promise<Flight> {
    const response = await request(`${flightPath(key)}/departure`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ actualDeparture }),
    });
    return response.json();
}

export async function recordArrival(key: FlightKey, actualArrival: string): Promise<Flight> {
    const response = await request(`${flightPath(key)}/arrival`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ actualArrival }),
    });
    return response.json();
}

// ends the flight and asks the planning system for the new one; answers the old flight
export async function replanFlight(key: FlightKey, request: ReplanFlightRequest): Promise<Flight> {
    const response = await requestJson(`${flightPath(key)}/replanning`, request);
    return response.json();
}

async function requestJson(url: string, body: ReplanFlightRequest): Promise<Response> {
    return request(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
    });
}

export function flightDetailsPage(key: FlightKey): string {
    return `flight-details.html?${new URLSearchParams(key)}`;
}

function flightPath(key: FlightKey): string {
    return `/api/flights/${encodeURIComponent(key.flightNumber)}/${encodeURIComponent(key.scheduledDepartureDate)}/${encodeURIComponent(key.departureAirportCode)}`;
}
