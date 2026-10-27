import { clearError, getJson, showError } from './api.js';
import { hasRole } from './auth.js';
import { requiredElement } from './dom.js';
import type { FlightOverview } from './flight-overview.js';
import { loadLoadingWindow, renderLoadingWindow } from './loading-window.js';
import { initializePage } from './page.js';
import {
    changePosition,
    loadPositions,
    renderPositionSelect,
    takenPositions,
    type LoadingPositionResponse,
} from './positions.js';
import { formatBrusselsDateTime } from './time.js';
import type { UldBookingResponse } from './uld-booking.js';

// the cargo plan of one flight as sent by CargoPlanResponse
type CargoPlanResponse = {
    flight: FlightOverview;
    bookings: UldBookingResponse[];
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
    // all or nothing so the page never shows half a plan
    const [cargoPlan, loadingWindow, positions] = await Promise.all([
        getJson<CargoPlanResponse>(path),
        loadLoadingWindow(path),
        loadPositions(),
    ]);
    if (!cargoPlan.ok) {
        renderError(cargoPlan.status, cargoPlan.message);
        return;
    }
    if (!loadingWindow.ok) {
        renderError(loadingWindow.status, loadingWindow.message);
        return;
    }
    if (!positions.ok) {
        renderError(positions.status, positions.message);
        return;
    }
    renderFlight(cargoPlan.data.flight);
    renderLoadingWindow(windowBlock, loadingWindow.data);
    const editablePath = hasRole('load-planner') && loadingWindow.data.loadingAllowed ? path : null;
    renderBookings(cargoPlan.data.bookings, positions.data, editablePath);
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

// editablePath is the plan path while loading is allowed and null otherwise
function renderBookings(
    bookings: UldBookingResponse[],
    positions: LoadingPositionResponse[],
    editablePath: string | null,
): void {
    const taken = takenPositions(bookings);
    rows.replaceChildren(
        ...bookings.map((booking) => bookingRow(booking, positionContent(booking, positions, taken, editablePath))),
    );
    tableStatus.textContent = bookings.length > 0 ? '' : 'No ULDs booked for this flight yet.';
}

function bookingRow(booking: UldBookingResponse, position: string | HTMLElement): HTMLTableRowElement {
    const row = document.createElement('tr');
    row.className = '*:border-b *:border-slate-300 *:px-3 *:py-2';
    row.append(
        cell(booking.uldCode),
        cell(weightFormat.format(booking.weight.value)),
        cell(booking.weight.unit),
        cell(booking.destination),
        cell(position),
    );
    return row;
}

// a dropdown while loading is allowed and the plain position otherwise
function positionContent(
    booking: UldBookingResponse,
    positions: LoadingPositionResponse[],
    taken: Map<string, string>,
    editablePath: string | null,
): string | HTMLSelectElement {
    if (editablePath === null) {
        // a booking without a position is shown too
        return booking.position ?? 'No position';
    }
    const select = renderPositionSelect(booking, positions, taken);
    select.addEventListener('change', () => savePosition(editablePath, booking.uldCode, select));
    return select;
}

async function savePosition(cargoPlanPath: string, uldCode: string, select: HTMLSelectElement): Promise<void> {
    select.disabled = true;
    const result = await changePosition(cargoPlanPath, uldCode, select.value);
    // reload after success and after a refusal so the page shows what the server stored
    await loadCargoPlan();
    if (!result.ok) {
        showError(`Position of ${uldCode} not changed: ${result.message}`);
    }
}

// append adds text as plain text and never as html
function cell(content: string | HTMLElement): HTMLTableCellElement {
    const td = document.createElement('td');
    td.append(content);
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
void initializePage(loadCargoPlan);
