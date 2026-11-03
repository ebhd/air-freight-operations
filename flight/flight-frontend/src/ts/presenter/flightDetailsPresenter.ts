import type { Delay } from '../domain/Delay';
import type { DelayReason } from '../domain/DelayReason';
import type { Flight, FlightKey } from '../domain/Flight';
import { hasRole } from '../infrastructure/auth';
import { getDelayReasons } from '../infrastructure/delayReasonRest';
import {
    addDepartureDelay,
    addArrivalDelay,
    getFlight,
    removeDepartureDelay,
    removeArrivalDelay,
    updateDepartureDelay,
    updateArrivalDelay,
} from '../infrastructure/flightRest';
import { ResponseError } from '../infrastructure/restUtils';
import { clearError, showError } from './messagePresenter';
import { formatBrusselsDateTime } from './timeFormat';
import { initializeUser } from './userPresenter';

const content = document.querySelector<HTMLElement>('#content')!;
const pageStatus = document.querySelector<HTMLElement>('#page-status')!;
const departureDelayTableBody = document.querySelector<HTMLTableSectionElement>('#departure-delays')!;
const arrivalDelayTableBody = document.querySelector<HTMLTableSectionElement>('#arrival-delays')!;
const delayEditor = document.querySelector<HTMLElement>('#delay-editor')!;
const editorTitle = document.querySelector<HTMLElement>('#editor-title')!;
const delayForm = document.querySelector<HTMLFormElement>('#delay-form')!;
const delayFields = document.querySelector<HTMLFieldSetElement>('#delay-fields')!;
const categoryInput = document.querySelector<HTMLSelectElement>('#delay-category')!;
const minutesInput = document.querySelector<HTMLInputElement>('#delay-minutes')!;
const reasonInput = document.querySelector<HTMLSelectElement>('#delay-reason')!;
const descriptionInput = document.querySelector<HTMLInputElement>('#delay-description')!;
const saveButton = document.querySelector<HTMLButtonElement>('#save-delay')!;
const cancelButton = document.querySelector<HTMLButtonElement>('#cancel-edit')!;
const refreshButton = document.querySelector<HTMLButtonElement>('#refresh')!;
const permissionsMessage = document.querySelector<HTMLElement>('#permissions')!;
const departureDelayStatus = document.querySelector<HTMLElement>('#departure-delay-status')!;
const arrivalDelayStatus = document.querySelector<HTMLElement>('#arrival-delay-status')!;
let flightKey: FlightKey;
let currentFlight: Flight | null = null;
let delayReasons: DelayReason[] = [];
let delayBeingEdited: Delay | null = null;
let editingDeparture = false;
let isBusy = false;

function canChangeDelay(departure: boolean): boolean {
    if (!hasRole('flight-planner') || currentFlight === null) {
        return false;
    }
    if (currentFlight.status === 'PLANNED') {
        return true;
    }
    return !departure && currentFlight.status === 'DEPARTED';
}

function setBusy(busy: boolean): void {
    isBusy = busy;
    delayFields.disabled = busy;
    refreshButton.disabled = busy;
    for (const button of document.querySelectorAll<HTMLButtonElement>('#departure-delays button, #arrival-delays button')) {
        button.disabled = busy;
    }
}

function createReasonOption(label: string, code: string): HTMLOptionElement {
    const option = document.createElement('option');
    option.textContent = label;
    option.value = code;
    return option;
}

function resetDelayEditor(): void {
    delayBeingEdited = null;
    delayForm.reset();
    categoryInput.disabled = false;
    reasonInput.disabled = false;
    const departureOption = categoryInput.querySelector<HTMLOptionElement>('[value="departure"]')!;
    const canChangeDeparture = canChangeDelay(true);
    const canChangeArrival = canChangeDelay(false);
    departureOption.disabled = !canChangeDeparture;
    if (canChangeDeparture) {
        categoryInput.value = 'departure';
    } else {
        categoryInput.value = 'arrival';
    }
    reasonInput.replaceChildren(createReasonOption('Choose a reason', ''));
    for (const reason of delayReasons) {
        const label = `${reason.code} — ${reason.label}`;
        reasonInput.add(createReasonOption(label, reason.code));
    }
    saveButton.textContent = 'Add delay';
    editorTitle.textContent = 'Add delay';
    cancelButton.hidden = true;
    delayEditor.hidden = !(canChangeDeparture || canChangeArrival) || delayReasons.length === 0;
}

function displayEstimate(name: string, scheduled: string, estimated: string): void {
    const estimateChanged = Date.parse(scheduled) !== Date.parse(estimated);
    const estimateValue = document.querySelector<HTMLElement>(`#estimated-${name}`)!;
    const estimateLabel = document.querySelector<HTMLElement>(`#estimated-${name}-label`)!;
    estimateLabel.hidden = !estimateChanged;
    estimateValue.hidden = !estimateChanged;
    estimateValue.textContent = '';
    if (estimateChanged) {
        estimateValue.textContent = formatBrusselsDateTime(estimated);
    }
}

function displayFlightDetails(flight: Flight): void {
    document.querySelector<HTMLElement>('#flight-number')!.textContent = flight.flightNumber;
    document.querySelector<HTMLElement>('#flight-date')!.textContent = flight.scheduledDepartureDate;
    document.querySelector<HTMLElement>('#flight-from')!.textContent = flight.departureAirportCode;
    document.querySelector<HTMLElement>('#flight-to')!.textContent = flight.arrivalAirportCode;
    document.querySelector<HTMLElement>('#flight-status')!.textContent = flight.status;
    document.querySelector<HTMLElement>('#scheduled-departure')!.textContent = formatBrusselsDateTime(flight.scheduledDeparture);
    document.querySelector<HTMLElement>('#scheduled-arrival')!.textContent = formatBrusselsDateTime(flight.scheduledArrival);
    displayEstimate('departure', flight.scheduledDeparture, flight.estimatedDeparture);
    displayEstimate('arrival', flight.scheduledArrival, flight.estimatedArrival);
}

function displayDelays(delays: Delay[], tableBody: HTMLTableSectionElement, departure: boolean): void {
    tableBody.replaceChildren();
    for (const delay of delays) {
        tableBody.append(createDelayRow(delay, departure));
    }
}

function displayPermissions(flight: Flight): void {
    if (!hasRole('flight-planner')) {
        permissionsMessage.textContent = 'Read-only access. Flight planners can manage delays.';
    } else if (flight.status === 'LANDED') {
        permissionsMessage.textContent = 'This flight has landed. All delays are frozen.';
    } else if (flight.status === 'DEPARTED') {
        permissionsMessage.textContent = 'This flight has departed. Only arrival delays can change.';
    } else {
        permissionsMessage.textContent = '';
    }
}

function displayFlight(flight: Flight): void {
    currentFlight = flight;
    displayFlightDetails(flight);
    displayDelays(flight.departureDelays, departureDelayTableBody, true);
    displayDelays(flight.arrivalDelays, arrivalDelayTableBody, false);
    departureDelayStatus.hidden = flight.departureDelays.length > 0;
    arrivalDelayStatus.hidden = flight.arrivalDelays.length > 0;
    displayPermissions(flight);
    resetDelayEditor();
    content.hidden = false;
}

function createDelayRow(delay: Delay, departure: boolean): HTMLTableRowElement {
    const row = document.createElement('tr');
    row.className = '*:border-b *:border-slate-300 *:px-3 *:py-2';
    let reasonLabel = delay.reasonCode;
    const reason = delayReasons.find(reason => reason.code === delay.reasonCode);
    if (reason) {
        reasonLabel = `${reason.code} — ${reason.label}`;
    }
    const values = [String(delay.minutes), reasonLabel, delay.description];
    for (const text of values) {
        const cell = document.createElement('td');
        cell.textContent = text;
        row.append(cell);
    }
    const actions = document.createElement('td');
    if (canChangeDelay(departure)) {
        const edit = document.createElement('button');
        edit.type = 'button';
        edit.className = 'button mr-2';
        edit.textContent = 'Edit';
        edit.disabled = isBusy;
        edit.addEventListener('click', () => editDelay(delay, departure));
        const remove = document.createElement('button');
        remove.type = 'button';
        remove.className = 'button';
        remove.textContent = 'Delete';
        remove.disabled = isBusy;
        remove.addEventListener('click', () => deleteDelay(delay, departure));
        actions.append(edit, remove);
    }
    row.append(actions);
    return row;
}

function editDelay(delay: Delay, departure: boolean): void {
    if (isBusy || !canChangeDelay(departure)) {
        return;
    }
    clearError();
    pageStatus.textContent = '';
    delayBeingEdited = delay;
    editingDeparture = departure;
    if (departure) {
        categoryInput.value = 'departure';
    } else {
        categoryInput.value = 'arrival';
    }
    categoryInput.disabled = true;
    let reasonListed = false;
    for (const option of reasonInput.options) {
        if (option.value === delay.reasonCode) {
            reasonListed = true;
            break;
        }
    }
    if (!reasonListed) {
        reasonInput.add(createReasonOption(delay.reasonCode, delay.reasonCode));
    }
    reasonInput.value = delay.reasonCode;
    reasonInput.disabled = true;
    minutesInput.value = String(delay.minutes);
    descriptionInput.value = delay.description;
    editorTitle.textContent = 'Edit delay';
    saveButton.textContent = 'Save changes';
    cancelButton.hidden = false;
    delayEditor.hidden = false;
    minutesInput.focus();
}

async function refreshFlight(): Promise<Flight> {
    try {
        const flight = await getFlight(flightKey);
        displayFlight(flight);
        return flight;
    } catch (error) {
        currentFlight = null;
        delayEditor.hidden = true;
        departureDelayTableBody.replaceChildren();
        arrivalDelayTableBody.replaceChildren();
        content.hidden = true;
        throw error;
    }
}

// a refusal or a missing flight or delay means the page shows an old state
async function reloadAfterRefusal(error: unknown): Promise<void> {
    if (!(error instanceof ResponseError)) {
        return;
    }
    if (error.status !== 404 && error.status !== 409) {
        return;
    }
    try {
        await refreshFlight();
    } catch {
        // the flight itself is gone so the server message below is enough
    }
    showError(error);
}

async function saveDelay(event: Event): Promise<void> {
    event.preventDefault();
    if (isBusy || !delayForm.reportValidity()) {
        return;
    }
    clearError();
    pageStatus.textContent = '';
    let departure = categoryInput.value === 'departure';
    if (delayBeingEdited !== null) {
        departure = editingDeparture;
    }
    const minutes = Number(minutesInput.value);
    const description = descriptionInput.value.trim();
    if (!canChangeDelay(departure) || !Number.isInteger(minutes) || minutes < 1 || minutes > 720 || !description) {
        showError(new Error('Choose an allowed delay category, 1–720 whole minutes and a description.'));
        return;
    }
    setBusy(true);
    let delaySaved = false;
    try {
        if (delayBeingEdited !== null) {
            if (departure) {
                await updateDepartureDelay(flightKey, delayBeingEdited.id, { minutes, description });
            } else {
                await updateArrivalDelay(flightKey, delayBeingEdited.id, { minutes, description });
            }
            delaySaved = true;
            await refreshFlight();
        } else {
            const delay = { minutes, reasonCode: reasonInput.value, description };
            let flight: Flight;
            if (departure) {
                flight = await addDepartureDelay(flightKey, delay);
            } else {
                flight = await addArrivalDelay(flightKey, delay);
            }
            displayFlight(flight);
            delaySaved = true;
        }
        pageStatus.textContent = 'Delay saved. Flight times are up to date.';
    } catch (error) {
        if (delaySaved) {
            showError(new Error('The delay was saved, but the flight could not be refreshed. Reload the page.'));
        } else {
            showError(error);
            await reloadAfterRefusal(error);
        }
    } finally {
        setBusy(false);
    }
}

async function deleteDelay(delay: Delay, departure: boolean): Promise<void> {
    if (isBusy || !canChangeDelay(departure)) {
        return;
    }
    if (!confirm('Delete this delay? Partners will be notified of the new times.')) {
        return;
    }
    clearError();
    pageStatus.textContent = '';
    setBusy(true);
    let delayRemoved = false;
    try {
        if (departure) {
            await removeDepartureDelay(flightKey, delay.id);
        } else {
            await removeArrivalDelay(flightKey, delay.id);
        }
        delayRemoved = true;
        await refreshFlight();
        pageStatus.textContent = 'Delay deleted. Flight times are up to date.';
    } catch (error) {
        if (delayRemoved) {
            showError(new Error('The delay was deleted, but the flight could not be refreshed. Reload the page.'));
        } else {
            showError(error);
            await reloadAfterRefusal(error);
        }
    } finally {
        setBusy(false);
    }
}

async function handleRefreshClick(): Promise<void> {
    clearError();
    pageStatus.textContent = '';
    setBusy(true);
    try {
        await refreshFlight();
    } catch (error) {
        showError(error);
    } finally {
        setBusy(false);
    }
}

async function initialize(): Promise<void> {
    try {
        await initializeUser();
        const query = new URLSearchParams(window.location.search);
        const flightNumber = query.get('flightNumber');
        const scheduledDepartureDate = query.get('scheduledDepartureDate');
        const departureAirportCode = query.get('departureAirportCode');
        if (!flightNumber || !scheduledDepartureDate || !departureAirportCode) {
            throw new Error('Choose a flight from the overview first.');
        }
        flightKey = { flightNumber, scheduledDepartureDate, departureAirportCode };
        document.querySelector<HTMLAnchorElement>('#back-to-flights')!.href = `../index.html?${new URLSearchParams({ date: scheduledDepartureDate })}`;
        pageStatus.textContent = 'Loading…';
        const flight = await refreshFlight();
        try {
            delayReasons = await getDelayReasons();
            displayFlight(flight);
        } catch {
            showError(new Error('Delay reasons could not be loaded. Refresh the page before adding a delay.'));
        }
        pageStatus.textContent = '';
        delayForm.addEventListener('submit', saveDelay);
        cancelButton.addEventListener('click', resetDelayEditor);
        refreshButton.addEventListener('click', handleRefreshClick);
    } catch (error) {
        pageStatus.textContent = '';
        showError(error);
    }
}

void initialize();
