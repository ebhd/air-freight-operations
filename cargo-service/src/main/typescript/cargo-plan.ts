import { clearError, getJson, showError } from './api.js';
import { whenSignedIn } from './auth.js';
import { requiredElement } from './dom.js';
import type { FlightOverview } from './flight-overview.js';
import { loadLoadingWindow, renderLoadingWindow } from './loading-window.js';
import { formatBrusselsDateTime } from './time.js';

// the cargo plan of one flight as sent by CargoPlanResponse
type CargoPlanResponse = {
    flight: FlightOverview;
    bookings: UldBookingResponse[];
};

type UldBookingResponse = {
    uldCode: string;
    weight: WeightValue;
    destination: string;
    position: string | null;
};

type WeightValue = {
    value: number;
    unit: string;
};

type FlightKey = {
    flightNumber: string;
    scheduledDepartureDate: string;
    departureAirportCode: string;
};

const INVALID_LINK = 'Invalid link to the cargo plan.';

const flightBlock = requiredElement('#flight', HTMLDListElement);
const windowBlock = requiredElement('#loading-window', HTMLDListElement);
const rows = requiredElement('#bookings', HTMLTableSectionElement);
const tableStatus = requiredElement('#table-status', HTMLElement);
const retryButton = requiredElement('#retry', HTMLButtonElement);

// shows the booked weight as is without converting units
const weightFormat = new Intl.NumberFormat('en-GB', { maximumFractionDigits: 3 });

// null when a part of the key is missing so the page sends no request
function readFlightKey(params: URLSearchParams): FlightKey | null {
    const flightNumber = params.get('flightNumber');
    const scheduledDepartureDate = params.get('scheduledDepartureDate');
    const departureAirportCode = params.get('departureAirportCode');
    if (!flightNumber || !scheduledDepartureDate || !departureAirportCode) {
        return null;
    }
    return { flightNumber, scheduledDepartureDate, departureAirportCode };
}

// encoding keeps a slash in a value from changing the path
function cargoPlanPath(flightKey: FlightKey): string {
    const parts = [flightKey.flightNumber, flightKey.scheduledDepartureDate, flightKey.departureAirportCode];
    return `/api/cargo-plans/${parts.map(encodeURIComponent).join('/')}`;
}

async function loadCargoPlan(): Promise<void> {
    clearError();
    retryButton.hidden = true;
    flightBlock.hidden = true;
    windowBlock.hidden = true;
    rows.replaceChildren();
    const flightKey = readFlightKey(new URLSearchParams(location.search));
    if (!flightKey) {
        tableStatus.textContent = '';
        showError(INVALID_LINK);
        return;
    }
    tableStatus.textContent = 'Loading…';
    const path = cargoPlanPath(flightKey);
    // both or nothing so the page never shows half a plan
    const [cargoPlan, loadingWindow] = await Promise.all([
        getJson<CargoPlanResponse>(path),
        loadLoadingWindow(path),
    ]);
    if (!cargoPlan.ok) {
        renderError(cargoPlan.status, cargoPlan.message);
        return;
    }
    if (!loadingWindow.ok) {
        renderError(loadingWindow.status, loadingWindow.message);
        return;
    }
    renderFlight(cargoPlan.data.flight);
    renderLoadingWindow(windowBlock, loadingWindow.data);
    renderBookings(cargoPlan.data.bookings);
}

function renderFlight(flight: FlightOverview): void {
    setText('#flight-number', flight.flightNumber);
    setText('#flight-date', flight.scheduledDepartureDate);
    setText('#flight-from', flight.departureAirportCode);
    setText('#flight-departure', formatBrusselsDateTime(flight.scheduledDeparture));
    setText('#flight-to', flight.arrivalAirportCode);
    setText('#flight-arrival', formatBrusselsDateTime(flight.scheduledArrival));
    setText('#flight-status', flight.status);
    flightBlock.hidden = false;
}

// textContent never runs server text as html
function setText(selector: string, text: string): void {
    requiredElement(selector, HTMLElement).textContent = text;
}

function renderBookings(bookings: UldBookingResponse[]): void {
    rows.replaceChildren(...bookings.map(bookingRow));
    tableStatus.textContent = bookings.length > 0 ? '' : 'No ULDs booked for this flight yet.';
}

function bookingRow(booking: UldBookingResponse): HTMLTableRowElement {
    const row = document.createElement('tr');
    row.className = '*:border-b *:border-slate-300 *:px-3 *:py-2';
    row.append(
        cell(booking.uldCode),
        cell(weightFormat.format(booking.weight.value)),
        cell(booking.weight.unit),
        cell(booking.destination),
        // a booking without a position is shown too
        cell(booking.position ?? 'No position'),
    );
    return row;
}

// append adds text as plain text and never as html
function cell(text: string): HTMLTableCellElement {
    const td = document.createElement('td');
    td.append(text);
    return td;
}

function renderError(status: number | null, message: string): void {
    tableStatus.textContent = '';
    showError(errorText(status, message));
    // flight down is temporary so a retry can help
    retryButton.hidden = status !== 503;
}

// the server message is shown unless the page can explain the status better
function errorText(status: number | null, message: string): string {
    if (status === 400) {
        return INVALID_LINK;
    }
    if (status === 404) {
        return 'Flight not found. Its data may already have been cleaned up.';
    }
    return message;
}

retryButton.addEventListener('click', loadCargoPlan);
whenSignedIn(loadCargoPlan);
