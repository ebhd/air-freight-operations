import type { Delay, DelayType } from '../domain/Delay';
import type { DelayReason } from '../domain/DelayReason';
import type { Flight, FlightKey } from '../domain/Flight';
import { hasRole } from '../infrastructure/auth';
import { getDelayReasons } from '../infrastructure/delayReasonRest';
import { addDelay, getFlight, removeDelay, updateDelay } from '../infrastructure/flightRest';
import { clearError, showError } from './messagePresenter';
import { formatBrusselsDateTime } from './timeFormat';
import { initializeUser } from './userPresenter';

const content = document.querySelector<HTMLElement>('#content')!;
const pageStatus = document.querySelector<HTMLElement>('#page-status')!;
const rows = document.querySelector<HTMLTableSectionElement>('#delays')!;
const editor = document.querySelector<HTMLElement>('#delay-editor')!;
const form = document.querySelector<HTMLFormElement>('#delay-form')!;
const fields = document.querySelector<HTMLFieldSetElement>('#delay-fields')!;
const typeInput = document.querySelector<HTMLSelectElement>('#delay-type')!;
const minutesInput = document.querySelector<HTMLInputElement>('#delay-minutes')!;
const reasonInput = document.querySelector<HTMLSelectElement>('#delay-reason')!;
const descriptionInput = document.querySelector<HTMLInputElement>('#delay-description')!;
const saveButton = document.querySelector<HTMLButtonElement>('#save-delay')!;
const cancelButton = document.querySelector<HTMLButtonElement>('#cancel-edit')!;
const refreshButton = document.querySelector<HTMLButtonElement>('#refresh')!;
let flightKey: FlightKey;
let flight: Flight | null = null;
let reasons: DelayReason[] = [];
let editing: Delay | null = null;
let busy = false;

function canChange(type: DelayType): boolean {
    return hasRole('flight-planner') && flight !== null
        && (flight.status === 'PLANNED' || (type === 'ARRIVAL' && flight.status === 'DEPARTED'));
}

function setBusy(value: boolean): void {
    busy = value;
    fields.disabled = value;
    refreshButton.disabled = value;
    rows.querySelectorAll('button').forEach(button => { button.disabled = value; });
}

function option(label: string, value: string): HTMLOptionElement {
    const item = document.createElement('option');
    item.textContent = label;
    item.value = value;
    return item;
}

function resetEditor(): void {
    editing = null;
    form.reset();
    typeInput.disabled = false;
    reasonInput.disabled = false;
    const departure = typeInput.querySelector<HTMLOptionElement>('[value="DEPARTURE"]')!;
    departure.disabled = !canChange('DEPARTURE');
    typeInput.value = canChange('DEPARTURE') ? 'DEPARTURE' : 'ARRIVAL';
    reasonInput.replaceChildren(option('Choose a reason', ''), ...reasons.map(reason => option(`${reason.code} — ${reason.label}`, reason.code)));
    saveButton.textContent = 'Add delay';
    document.querySelector<HTMLElement>('#editor-title')!.textContent = 'Add delay';
    cancelButton.hidden = true;
    editor.hidden = !(canChange('DEPARTURE') || canChange('ARRIVAL')) || reasons.length === 0;
}

function showEstimate(name: string, scheduled: string, estimated: string): void {
    const changed = Date.parse(scheduled) !== Date.parse(estimated);
    const value = document.querySelector<HTMLElement>(`#estimated-${name}`)!;
    document.querySelector<HTMLElement>(`#estimated-${name}-label`)!.hidden = !changed;
    value.hidden = !changed;
    value.textContent = changed ? formatBrusselsDateTime(estimated) : '';
}

function renderFlight(current: Flight): void {
    flight = current;
    const values: Record<string, string> = {
        'flight-number': current.flightNumber,
        'flight-date': current.scheduledDepartureDate,
        'flight-from': current.departureAirportCode,
        'flight-to': current.arrivalAirportCode,
        'flight-status': current.status,
        'scheduled-departure': formatBrusselsDateTime(current.scheduledDeparture),
        'scheduled-arrival': formatBrusselsDateTime(current.scheduledArrival),
    };
    for (const [id, text] of Object.entries(values)) {
        document.getElementById(id)!.textContent = text;
    }
    showEstimate('departure', current.scheduledDeparture, current.estimatedDeparture);
    showEstimate('arrival', current.scheduledArrival, current.estimatedArrival);
    rows.replaceChildren(...current.delays.map(delayRow));
    document.querySelector<HTMLElement>('#delay-status')!.textContent = current.delays.length === 0 ? 'No delays recorded.' : '';
    document.querySelector<HTMLElement>('#permissions')!.textContent = !hasRole('flight-planner')
        ? 'Read-only access. Flight planners can manage delays.'
        : current.status === 'LANDED' ? 'This flight has landed. All delays are frozen.'
        : current.status === 'DEPARTED' ? 'This flight has departed. Only arrival delays can change.' : '';
    resetEditor();
    content.hidden = false;
}

function delayRow(delay: Delay): HTMLTableRowElement {
    const row = document.createElement('tr');
    row.className = '*:border-b *:border-slate-300 *:px-3 *:py-2';
    const reason = reasons.find(item => item.code === delay.reasonCode);
    for (const text of [delay.type === 'DEPARTURE' ? 'Departure' : 'Arrival', String(delay.minutes), reason ? `${reason.code} — ${reason.label}` : delay.reasonCode, delay.description]) {
        const cell = document.createElement('td');
        cell.textContent = text;
        row.append(cell);
    }
    const actions = document.createElement('td');
    if (canChange(delay.type)) {
        const edit = document.createElement('button');
        edit.type = 'button';
        edit.className = 'button mr-2';
        edit.textContent = 'Edit';
        edit.addEventListener('click', () => editDelay(delay));
        const remove = document.createElement('button');
        remove.type = 'button';
        remove.className = 'button';
        remove.textContent = 'Delete';
        remove.addEventListener('click', () => deleteDelay(delay));
        actions.append(edit, remove);
    }
    row.append(actions);
    return row;
}

function editDelay(delay: Delay): void {
    if (busy || !canChange(delay.type)) {
        return;
    }
    clearError();
    pageStatus.textContent = '';
    editing = delay;
    typeInput.value = delay.type;
    typeInput.disabled = true;
    if (!Array.from(reasonInput.options).some(option => option.value === delay.reasonCode)) {
        reasonInput.add(option(delay.reasonCode, delay.reasonCode));
    }
    reasonInput.value = delay.reasonCode;
    reasonInput.disabled = true;
    minutesInput.value = String(delay.minutes);
    descriptionInput.value = delay.description;
    document.querySelector<HTMLElement>('#editor-title')!.textContent = 'Edit delay';
    saveButton.textContent = 'Save changes';
    cancelButton.hidden = false;
    editor.hidden = false;
    minutesInput.focus();
}

async function refreshFlight(): Promise<void> {
    try {
        renderFlight(await getFlight(flightKey));
    } catch (error) {
        flight = null;
        editor.hidden = true;
        rows.replaceChildren();
        content.hidden = true;
        throw error;
    }
}

async function saveDelay(event: Event): Promise<void> {
    event.preventDefault();
    if (busy || !form.reportValidity()) {
        return;
    }
    clearError();
    pageStatus.textContent = '';
    const type = editing?.type ?? typeInput.value as DelayType;
    const minutes = Number(minutesInput.value);
    const description = descriptionInput.value.trim();
    if (!canChange(type) || !Number.isInteger(minutes) || minutes < 1 || minutes > 720 || !description) {
        showError(new Error('Choose an allowed delay type, 1–720 whole minutes and a description.'));
        return;
    }
    setBusy(true);
    let saved = false;
    try {
        if (editing) {
            await updateDelay(flightKey, editing.id, { minutes, description });
            saved = true;
            await refreshFlight();
        } else {
            renderFlight(await addDelay(flightKey, { type, minutes, reasonCode: reasonInput.value, description }));
            saved = true;
        }
        pageStatus.textContent = 'Delay saved. Flight times are up to date.';
    } catch (error) {
        showError(saved ? new Error('The delay was saved, but the flight could not be refreshed. Reload the page.') : error);
    } finally {
        setBusy(false);
    }
}

async function deleteDelay(delay: Delay): Promise<void> {
    if (busy || !canChange(delay.type)) {
        return;
    }
    clearError();
    pageStatus.textContent = '';
    setBusy(true);
    let removed = false;
    try {
        await removeDelay(flightKey, delay.id);
        removed = true;
        await refreshFlight();
        pageStatus.textContent = 'Delay deleted. Flight times are up to date.';
    } catch (error) {
        showError(removed ? new Error('The delay was deleted, but the flight could not be refreshed. Reload the page.') : error);
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
        await refreshFlight();
        try {
            reasons = await getDelayReasons();
            renderFlight(flight!);
        } catch {
            showError(new Error('Delay reasons could not be loaded. Refresh the page before adding a delay.'));
        }
        pageStatus.textContent = '';
        form.addEventListener('submit', saveDelay);
        cancelButton.addEventListener('click', resetEditor);
        refreshButton.addEventListener('click', async () => {
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
        });
    } catch (error) {
        pageStatus.textContent = '';
        showError(error);
    }
}

void initialize();
