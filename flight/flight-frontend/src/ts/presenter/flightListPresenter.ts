import type { FlightOverview } from '../domain/Flight';
import { getFlights } from '../infrastructure/flightRest';
import { clearError, showError } from './messagePresenter';
import { formatBrusselsDateTime } from './timeFormat';
import { initializeUser } from './userPresenter';

const dateInput = document.querySelector<HTMLInputElement>('#date')!;
const rows = document.querySelector<HTMLTableSectionElement>('#flights')!;
const tableStatus = document.querySelector<HTMLElement>('#table-status')!;

let latestRequest = 0;

async function showFlights(): Promise<void> {
    const request = ++latestRequest;
    const date = dateInput.value;
    clearError();
    rows.replaceChildren();
    tableStatus.textContent = '';
    if (!date || !dateInput.validity.valid) {
        showError(new Error('Choose a valid date.'));
        return;
    }
    tableStatus.textContent = 'Loading…';
    try {
        const flights = await getFlights(date);
        if (request !== latestRequest) {
            return;
        }
        rows.replaceChildren(...flights.map(flightRow));
        tableStatus.textContent = flights.length === 0 ? `No flights on ${date}.` : '';
    } catch (error) {
        if (request === latestRequest) {
            tableStatus.textContent = '';
            showError(error);
        }
    }
}

function flightRow(flight: FlightOverview): HTMLTableRowElement {
    const link = document.createElement('a');
    const key = new URLSearchParams({
        flightNumber: flight.flightNumber,
        scheduledDepartureDate: flight.scheduledDepartureDate,
        departureAirportCode: flight.departureAirportCode,
    });
    link.href = `html/flight-details.html?${key}`;
    link.className = 'font-medium text-blue-800 underline hover:text-blue-600';
    link.textContent = flight.flightNumber;
    const row = document.createElement('tr');
    row.className = '*:border-b *:border-slate-300 *:px-3 *:py-2';
    row.append(
        cell(link),
        cell(flight.scheduledDepartureDate),
        cell(flight.departureAirportCode),
        timeCell(flight.scheduledDeparture, flight.estimatedDeparture),
        cell(flight.arrivalAirportCode),
        timeCell(flight.scheduledArrival, flight.estimatedArrival),
        cell(flight.status),
    );
    return row;
}

function cell(text: string | HTMLElement): HTMLTableCellElement {
    const td = document.createElement('td');
    td.append(text);
    return td;
}

function timeCell(scheduled: string, estimated: string): HTMLTableCellElement {
    const td = cell(formatBrusselsDateTime(scheduled));
    if (Date.parse(scheduled) !== Date.parse(estimated)) {
        const expected = document.createElement('span');
        expected.className = 'block text-sm font-medium text-amber-800';
        expected.textContent = `Expected: ${formatBrusselsDateTime(estimated)}`;
        td.append(expected);
    }
    return td;
}

async function initialize(): Promise<void> {
    try {
        await initializeUser();
    } catch {
        showError(new Error('Sign-in could not be completed. Check Keycloak and reload the page.'));
        return;
    }
    dateInput.value = new URLSearchParams(window.location.search).get('date') ?? new Date().toISOString().slice(0, 10);
    dateInput.addEventListener('change', showFlights);
    document.querySelector<HTMLElement>('#content')!.hidden = false;
    await showFlights();
}

void initialize();
