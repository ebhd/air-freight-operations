import type { Delay } from '../../domain/Delay';
import type { DelayReason } from '../../domain/DelayReason';
import type { Flight } from '../../domain/Flight';

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
const departureDelayStatus = document.querySelector<HTMLElement>('#departure-delay-status')!;
const arrivalDelayStatus = document.querySelector<HTMLElement>('#arrival-delay-status')!;

let editDelay: (delay: Delay, departure: boolean) => void;
let deleteDelay: (delay: Delay, departure: boolean) => Promise<void>;

function createReasonOption(label: string, code: string): HTMLOptionElement {
    const option = document.createElement('option');
    option.textContent = label;
    option.value = code;
    return option;
}

export function resetDelayForm(delayReasons: DelayReason[], canChangeDeparture: boolean, canChangeArrival: boolean): void {
    delayForm.reset();
    categoryInput.disabled = false;
    reasonInput.disabled = false;
    const departureOption = categoryInput.querySelector<HTMLOptionElement>('[value="departure"]')!;
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

export function reasonText(code: string, delayReasons: DelayReason[]): string {
    const reason = delayReasons.find(reason => reason.code === code);
    return reason ? `${reason.code} — ${reason.label}` : code;
}

function createDelayRow(
    delay: Delay,
    departure: boolean,
    delayReasons: DelayReason[],
    editable: boolean,
    busy: boolean,
): HTMLTableRowElement {
    const row = document.createElement('tr');
    row.className = '*:border-b *:border-slate-300 *:px-3 *:py-2';
    const values = [String(delay.minutes), reasonText(delay.reasonCode, delayReasons), delay.description];
    for (const text of values) {
        const cell = document.createElement('td');
        cell.textContent = text;
        row.append(cell);
    }
    const actions = document.createElement('td');
    if (editable) {
        const edit = document.createElement('button');
        edit.type = 'button';
        edit.className = 'button mr-2';
        edit.innerHTML = '<i class="fa-solid fa-pen mr-1" aria-hidden="true"></i>Edit';
        edit.disabled = busy;
        edit.addEventListener('click', () => editDelay(delay, departure));
        const remove = document.createElement('button');
        remove.type = 'button';
        remove.className = 'button';
        remove.innerHTML = '<i class="fa-solid fa-trash mr-1" aria-hidden="true"></i>Delete';
        remove.disabled = busy;
        remove.addEventListener('click', () => deleteDelay(delay, departure));
        actions.append(edit, remove);
    }
    row.append(actions);
    return row;
}

export function displayDelayTables(
    flight: Flight,
    delayReasons: DelayReason[],
    canChangeDeparture: boolean,
    canChangeArrival: boolean,
    busy: boolean,
): void {
    departureDelayTableBody.replaceChildren();
    arrivalDelayTableBody.replaceChildren();
    for (const delay of flight.departureDelays) {
        departureDelayTableBody.append(createDelayRow(delay, true, delayReasons, canChangeDeparture, busy));
    }
    for (const delay of flight.arrivalDelays) {
        arrivalDelayTableBody.append(createDelayRow(delay, false, delayReasons, canChangeArrival, busy));
    }
    departureDelayStatus.hidden = flight.departureDelays.length > 0;
    arrivalDelayStatus.hidden = flight.arrivalDelays.length > 0;
}

export function showDelayEditor(delay: Delay, departure: boolean): void {
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

export function readDelayInput() {
    if (!delayForm.reportValidity()) {
        return null;
    }
    return {
        departure: categoryInput.value === 'departure',
        minutes: Number(minutesInput.value),
        description: descriptionInput.value.trim(),
        reasonCode: reasonInput.value,
    };
}

export function setDelaysBusy(busy: boolean): void {
    delayFields.disabled = busy;
    for (const button of document.querySelectorAll<HTMLButtonElement>('#departure-delays button, #arrival-delays button')) {
        button.disabled = busy;
    }
}

export function clearDelays(): void {
    delayEditor.hidden = true;
    departureDelayTableBody.replaceChildren();
    arrivalDelayTableBody.replaceChildren();
}

export function initializeDelays(
    save: (event: Event) => Promise<void>,
    cancel: () => void,
    edit: (delay: Delay, departure: boolean) => void,
    remove: (delay: Delay, departure: boolean) => Promise<void>,
): void {
    editDelay = edit;
    deleteDelay = remove;
    delayForm.addEventListener('submit', save);
    cancelButton.addEventListener('click', cancel);
}
