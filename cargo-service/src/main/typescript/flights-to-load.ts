import { clearError, getJson, showError } from './api.js';
import { whenSignedIn } from './auth.js';
import { requiredElement } from './dom.js';
import { formatBrusselsDateTime, utcToday } from './time.js';

// one row of GET /api/flights as sent by FlightOverviewResponse
type FlightOverview = {
    flightNumber: string;
    scheduledDepartureDate: string;
    departureAirportCode: string;
    arrivalAirportCode: string;
    status: string;
    scheduledDeparture: string;
    scheduledArrival: string;
};

const dateInput = requiredElement('#date', HTMLInputElement);
const includeDepartedInput = requiredElement('#include-departed', HTMLInputElement);
const rows = requiredElement('#flights', HTMLTableSectionElement);
const tableStatus = requiredElement('#table-status', HTMLElement);

async function loadFlights(): Promise<void> {
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
    row.className = '*:border-b *:border-slate-300 *:px-3 *:py-2';
    row.append(
        cell(link),
        cell(flight.scheduledDepartureDate),
        cell(flight.departureAirportCode),
        cell(formatBrusselsDateTime(flight.scheduledDeparture)),
        cell(flight.arrivalAirportCode),
        cell(formatBrusselsDateTime(flight.scheduledArrival)),
        cell(flight.status),
    );
    return row;
}

// append adds text as plain text and never as html
function cell(content: string | HTMLElement): HTMLTableCellElement {
    const td = document.createElement('td');
    td.append(content);
    return td;
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
whenSignedIn(loadFlights);
