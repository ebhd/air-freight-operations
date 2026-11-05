import type { Flight } from '../../domain/Flight';
import { hasRole } from '../../infrastructure/auth';
import { brusselsNowForInput, formatBrusselsDateTime } from '../timeFormat';

const actualTimes = document.querySelector<HTMLElement>('#actual-times')!;
const departureForm = document.querySelector<HTMLFormElement>('#departure-form')!;
const departureFields = document.querySelector<HTMLFieldSetElement>('#departure-fields')!;
const actualDepartureInput = document.querySelector<HTMLInputElement>('#actual-departure-input')!;
const arrivalForm = document.querySelector<HTMLFormElement>('#arrival-form')!;
const arrivalFields = document.querySelector<HTMLFieldSetElement>('#arrival-fields')!;
const actualArrivalInput = document.querySelector<HTMLInputElement>('#actual-arrival-input')!;

function displayActualTime(name: string, actual: string | null): void {
    const value = document.querySelector<HTMLElement>(`#actual-${name}`)!;
    const label = document.querySelector<HTMLElement>(`#actual-${name}-label`)!;
    label.hidden = actual === null;
    value.hidden = actual === null;
    value.textContent = actual === null ? '' : formatBrusselsDateTime(actual);
}

export function displayActualTimeForms(flight: Flight): void {
    const planner = hasRole('flight-planner');
    departureForm.hidden = !(planner && flight.status === 'PLANNED');
    arrivalForm.hidden = !(planner && flight.status === 'DEPARTED');
    actualTimes.hidden = departureForm.hidden && arrivalForm.hidden;
    const now = brusselsNowForInput();
    actualDepartureInput.value = now;
    actualArrivalInput.value = now;
}

export function displayActualTimes(flight: Flight): void {
    displayActualTime('departure', flight.actualDeparture);
    displayActualTime('arrival', flight.actualArrival);
}

export function setActualTimesBusy(busy: boolean): void {
    departureFields.disabled = busy;
    arrivalFields.disabled = busy;
}

export function hideActualTimeForms(): void {
    actualTimes.hidden = true;
}

export function readActualTime(departure: boolean): string | null {
    const form = departure ? departureForm : arrivalForm;
    if (!form.reportValidity()) {
        return null;
    }
    return departure ? actualDepartureInput.value : actualArrivalInput.value;
}

export function initializeActualTimes(register: (event: Event, departure: boolean) => Promise<void>): void {
    departureForm.addEventListener('submit', event => register(event, true));
    arrivalForm.addEventListener('submit', event => register(event, false));
}
