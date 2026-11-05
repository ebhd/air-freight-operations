import { clearError, getJson, postJson, showError } from '../shared/api.js';
import { hasRole } from '../shared/auth.js';
import { confirmDialog, type ConfirmDetail } from '../shared/confirm-dialog.js';
import { cell, requiredElement } from '../shared/dom.js';
import type { FlightOverview } from '../cargo/flight-overview.js';
import { renderLoadCheck, type LoadCheckReport } from '../cargo/load-check.js';
import { loadLoadingWindow, renderLoadingWindow } from '../cargo/loading-window.js';
import { initializePage } from '../shared/page.js';
import {
    changePosition,
    loadPositions,
    renderPositionSelect,
    takenPositions,
    type LoadingPositionResponse,
} from '../cargo/positions.js';
import {
    LOAD_PLANNER_ONLY,
    loadNotCheckedText,
    loadNotFinalizedText,
    positionNotChangedText,
} from '../cargo/refusal-text.js';
import { movedToLink, movedToText, type MovedTo } from '../cargo/replanning.js';
import { formatBrusselsDateTime } from '../shared/time.js';
import type { UldBookingResponse } from '../cargo/uld-booking.js';

// the cargo plan of one flight as sent by CargoPlanResponse
type CargoPlanResponse = {
    flight: FlightOverview;
    bookings: UldBookingResponse[];
    state: 'DRAFT' | 'FINALIZED' | 'REPLANNED';
    // null while the plan is a draft
    finalizedAt: string | null;
    // the new flight the cargo moved to, null unless the state is REPLANNED
    movedTo: MovedTo | null;
};

// the answer of a successful finalize as sent by FinalizationResponse
type FinalizationResponse = {
    finalizedAt: string;
    report: LoadCheckReport;
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
const readOnlyNote = requiredElement('#read-only', HTMLElement);
const loadCheckSection = requiredElement('#load-check', HTMLElement);
const checkButton = requiredElement('#check-load', HTMLButtonElement);
const finalizeButton = requiredElement('#finalize-load', HTMLButtonElement);
const finalizedNote = requiredElement('#finalized', HTMLElement);
const movedToNote = requiredElement('#moved-to', HTMLElement);
const movedToTextElement = requiredElement('#moved-to-text', HTMLElement);
const movedToLinkElement = requiredElement('#moved-to-link', HTMLAnchorElement);
const loadCheckStatus = requiredElement('#load-check-status', HTMLElement);
const loadCheckRows = requiredElement('#load-check-results', HTMLTableSectionElement);
readOnlyNote.textContent = `You can view this cargo plan. ${LOAD_PLANNER_ONLY}`;

// filled when the plan loads, so the finalize dialog can show what is being closed
let finalizeDetails: ConfirmDetail[] = [];

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
    readOnlyNote.hidden = true;
    loadCheckSection.hidden = true;
    finalizedNote.hidden = true;
    movedToNote.hidden = true;
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
    finalizeDetails = finalizeSummary(cargoPlan.data.flight, cargoPlan.data.bookings);
    renderFinalized(cargoPlan.data.finalizedAt);
    renderMovedTo(cargoPlan.data.movedTo);
    renderLoadingWindow(windowBlock, loadingWindow.data);
    const loadPlanner = hasRole('load-planner');
    // a flight planner may read the plan but gets no dropdowns so the page says why
    readOnlyNote.hidden = loadPlanner;
    // the cargo of a replanned flight lives on the new flight so nothing can change here
    const moved = cargoPlan.data.state === 'REPLANNED';
    const editablePath = loadPlanner && loadingWindow.data.loadingAllowed && !moved ? path : null;
    renderBookings(cargoPlan.data.bookings, positions.data, editablePath);
    // checks only run while loading is allowed so the report shows the current load
    if (loadingWindow.data.loadingAllowed && !moved) {
        checkButton.hidden = !loadPlanner;
        finalizeButton.hidden = !loadPlanner;
        loadCheckSection.hidden = false;
        await checkLoad(path);
    }
}

function renderFinalized(finalizedAt: string | null): void {
    finalizedNote.hidden = finalizedAt === null;
    finalizedNote.textContent =
        finalizedAt === null
            ? ''
            : `Load finalized at ${formatBrusselsDateTime(finalizedAt)} (Brussels). Positions can no longer change.`;
}

function renderMovedTo(movedTo: MovedTo | null): void {
    movedToNote.hidden = movedTo === null;
    if (movedTo !== null) {
        movedToTextElement.textContent = movedToText(movedTo);
        movedToLinkElement.href = movedToLink(movedTo);
        movedToLinkElement.textContent = `${movedTo.flightNumber} cargo plan`;
    }
}

// every reload checks again so the report never shows an older load
async function checkLoad(cargoPlanPath: string): Promise<void> {
    checkButton.disabled = true;
    // usable only after a report without a hard failure, the server checks again anyway
    finalizeButton.disabled = true;
    loadCheckStatus.textContent = 'Checking…';
    loadCheckRows.replaceChildren();
    // the check belongs to the cargo plan so its path continues the plan path
    const report = await getJson<LoadCheckReport>(`${cargoPlanPath}/load-check`);
    checkButton.disabled = false;
    if (!report.ok) {
        loadCheckStatus.textContent = loadNotCheckedText(report);
        return;
    }
    renderLoadCheck(loadCheckRows, report.data);
    finalizeButton.disabled = !report.data.hardChecksPassed;
    loadCheckStatus.textContent = report.data.hardChecksPassed
        ? 'All hard checks passed.'
        : 'A hard check failed, so the load cannot be finalized yet.';
}

function finalizeSummary(flight: FlightOverview, bookings: UldBookingResponse[]): ConfirmDetail[] {
    return [
        { label: 'Flight', value: `${flight.flightNumber}, ${flight.departureAirportCode} to ${flight.arrivalAirportCode}` },
        { label: 'Booked ULDs', value: String(bookings.length) },
    ];
}

async function finalizeLoad(cargoPlanPath: string): Promise<void> {
    const confirmed = await confirmDialog({
        title: 'Finalize the load?',
        message: 'Positions cannot be changed afterwards.',
        details: finalizeDetails,
        confirmLabel: 'Finalize load',
    });
    if (!confirmed) {
        return;
    }
    checkButton.disabled = true;
    finalizeButton.disabled = true;
    loadCheckStatus.textContent = 'Finalizing…';
    const result = await postJson<FinalizationResponse>(`${cargoPlanPath}/finalization`);
    if (!result.ok && result.loadCheckReport !== undefined) {
        // the checks ran again and a hard one failed so show that report
        renderLoadCheck(loadCheckRows, result.loadCheckReport);
        checkButton.disabled = false;
        loadCheckStatus.textContent = loadNotFinalizedText(result);
        return;
    }
    // reload after success and after other refusals so the page shows what the server stored
    await loadCargoPlan();
    if (!result.ok) {
        showError(loadNotFinalizedText(result));
    }
}

function renderFlight(flight: FlightOverview): void {
    setText('#flight-number', flight.flightNumber);
    setText('#flight-date', flight.scheduledDepartureDate);
    setText('#flight-from', flight.departureAirportCode);
    setText('#flight-departure', formatBrusselsDateTime(flight.scheduledDeparture));
    showEstimate('departure', flight.scheduledDeparture, flight.estimatedDeparture);
    setText('#flight-to', flight.arrivalAirportCode);
    setText('#flight-arrival', formatBrusselsDateTime(flight.scheduledArrival));
    showEstimate('arrival', flight.scheduledArrival, flight.estimatedArrival);
    setText('#flight-status', flight.status);
    requiredElement('#flight-status', HTMLElement).dataset.status = flight.status;
    flightBlock.hidden = false;
}

function showEstimate(name: 'departure' | 'arrival', scheduled: string, estimated: string): void {
    const changed = Date.parse(scheduled) !== Date.parse(estimated);
    requiredElement(`#flight-estimated-${name}-label`, HTMLElement).hidden = !changed;
    const value = requiredElement(`#flight-estimated-${name}`, HTMLElement);
    value.hidden = !changed;
    value.textContent = changed ? formatBrusselsDateTime(estimated) : '';
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
    row.className = 'data-row';
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
        showError(positionNotChangedText(uldCode, result));
    }
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
checkButton.addEventListener('click', () => {
    const flightKey = readFlightKey(new URLSearchParams(location.search));
    if (flightKey) {
        void checkLoad(cargoPlanPath(flightKey));
    }
});
finalizeButton.addEventListener('click', () => {
    const flightKey = readFlightKey(new URLSearchParams(location.search));
    if (flightKey) {
        void finalizeLoad(cargoPlanPath(flightKey));
    }
});
void initializePage(loadCargoPlan);
