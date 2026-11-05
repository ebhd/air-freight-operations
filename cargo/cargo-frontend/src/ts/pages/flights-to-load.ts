import { clearError, getJson, showError } from '../shared/api.js';
import { cell, requiredElement } from '../shared/dom.js';
import type { FlightOverview } from '../cargo/flight-overview.js';
import { initializePage } from '../shared/page.js';
import { formatBrusselsDateTime, utcToday } from '../shared/time.js';

const dateInput = requiredElement('#date', HTMLInputElement);
const includeDepartedInput = requiredElement('#include-departed', HTMLInputElement);
const rows = requiredElement('#flights', HTMLTableSectionElement);
const tableStatus = requiredElement('#table-status', HTMLElement);

let latestRequestId = 0;

async function loadFlights(): Promise<void> {
    const requestId = ++latestRequestId;
    clearError();
    rows.replaceChildren();
    if (!dateInput.value) {
        tableStatus.textContent = '';
        showError('Choose a date.');
        return;
    }
    tableStatus.textContent = 'Loading…';
    const query = new URLSearchParams({
        date: dateInput.value,
        includeDeparted: String(includeDepartedInput.checked),
    });
    const result = await getJson<FlightOverview[]>(`/api/flights?${query}`);
    if (requestId !== latestRequestId) {
        return;
    }
    if (!result.ok) {
        // an error is never shown as an empty day
        tableStatus.textContent = '';
        showError(result.message);
        return;
    }
    renderRows(result.data);
}

function renderRows(flights: FlightOverview[]): void {
    rows.replaceChildren(...flights.map(flightRow));
    if (flights.length > 0) {
        tableStatus.textContent = '';
    } else if (includeDepartedInput.checked) {
        tableStatus.textContent = `No flights to load on ${dateInput.value}.`;
    } else {
        tableStatus.textContent = `No flights to load on ${dateInput.value}. Departed flights are hidden.`;
    }
}

function flightRow(flight: FlightOverview): HTMLTableRowElement {
    const link = document.createElement('a');
    link.className = 'font-medium text-blue-800 underline hover:text-blue-600';
    link.href = cargoPlanUrl(flight);
    link.textContent = flight.flightNumber;

    const row = document.createElement('tr');
    row.className = 'data-row';
    row.append(
        cell(link),
        cell(flight.scheduledDepartureDate),
        cell(flight.departureAirportCode),
        cell(formatBrusselsDateTime(flight.scheduledDeparture)),
        cell(flight.arrivalAirportCode),
        cell(formatBrusselsDateTime(flight.scheduledArrival)),
        cell(statusBadge(flight.status)),
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

// the key comes from the row itself and not from the date input
function cargoPlanUrl(flight: FlightOverview): string {
    const flightKey = new URLSearchParams({
        flightNumber: flight.flightNumber,
        scheduledDepartureDate: flight.scheduledDepartureDate,
        departureAirportCode: flight.departureAirportCode,
    });
    return `cargo-plan.html?${flightKey}`;
}

dateInput.value = utcToday();
dateInput.addEventListener('change', loadFlights);
includeDepartedInput.addEventListener('change', loadFlights);
void initializePage(loadFlights);
