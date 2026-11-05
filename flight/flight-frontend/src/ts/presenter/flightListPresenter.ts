import type { FlightOverview } from '../domain/Flight';
import { getFlights } from '../infrastructure/flightRest';
import { clearError, showError } from './messagePresenter';
import { formatBrusselsDateTime } from './timeFormat';
import { initializeUser } from './userPresenter';

const dateInput = document.querySelector<HTMLInputElement>('#date')!;
const flightTableBody = document.querySelector<HTMLTableSectionElement>('#flights')!;
const tableStatus = document.querySelector<HTMLElement>('#table-status')!;

let latestRequestId = 0;

async function showFlights(): Promise<void> {
    latestRequestId += 1;
    const requestId = latestRequestId;
    const date = dateInput.value;
    clearError();
    flightTableBody.replaceChildren();
    tableStatus.textContent = '';
    if (!date || !dateInput.validity.valid) {
        showError(new Error('Choose a valid date.'));
        return;
    }
    tableStatus.textContent = 'Loading…';
    try {
        const flights = await getFlights(date);
        // A slower response for a previously selected date must not replace this list.
        if (requestId !== latestRequestId) {
            return;
        }
        for (const flight of flights) {
            flightTableBody.append(createFlightRow(flight));
        }
        tableStatus.textContent = '';
        if (flights.length === 0) {
            tableStatus.textContent = `No flights on ${date}.`;
        }
    } catch (error) {
        if (requestId === latestRequestId) {
            tableStatus.textContent = '';
            showError(error);
        }
    }
}

function createFlightRow(flight: FlightOverview): HTMLTableRowElement {
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
        createCell(link),
        createCell(flight.scheduledDepartureDate),
        createCell(flight.departureAirportCode),
        createTimeCell(flight.scheduledDeparture, flight.estimatedDeparture),
        createCell(flight.arrivalAirportCode),
        createTimeCell(flight.scheduledArrival, flight.estimatedArrival),
        createCell(statusBadge(flight.status)),
    );
    return row;
}

function statusBadge(status: string): HTMLElement {
    const badge = document.createElement('span');
    badge.className = 'status-badge';
    badge.dataset.status = status;
    badge.textContent = status;
    return badge;
}

function createCell(content: string | HTMLElement): HTMLTableCellElement {
    const cell = document.createElement('td');
    cell.append(content);
    return cell;
}

function createTimeCell(scheduled: string, estimated: string): HTMLTableCellElement {
    const cell = createCell(formatBrusselsDateTime(scheduled));
    if (Date.parse(scheduled) !== Date.parse(estimated)) {
        const estimate = document.createElement('span');
        estimate.className = 'block text-sm font-medium text-amber-800';
        estimate.textContent = `Expected: ${formatBrusselsDateTime(estimated)}`;
        cell.append(estimate);
    }
    return cell;
}

async function initialize(): Promise<void> {
    try {
        await initializeUser();
    } catch {
        showError(new Error('Sign-in could not be completed. Check Keycloak and reload the page.'));
        return;
    }
    const query = new URLSearchParams(window.location.search);
    const today = new Date().toISOString().slice(0, 10);
    dateInput.value = query.get('date') ?? today;
    dateInput.addEventListener('change', showFlights);
    document.querySelector<HTMLElement>('#content')!.hidden = false;
    await showFlights();
}

void initialize();
