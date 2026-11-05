import type { Delay } from '../../domain/Delay';
import type { Airport } from '../../domain/Airport';
import type { DelayReason } from '../../domain/DelayReason';
import type { Flight, FlightKey, ReplanFlightRequest } from '../../domain/Flight';
import { hasRole } from '../../infrastructure/auth';
import { getAirports } from '../../infrastructure/airportRest';
import { getDelayReasons } from '../../infrastructure/delayReasonRest';
import {
    addDepartureDelay,
    addArrivalDelay,
    getFlight,
    recordArrival,
    recordDeparture,
    removeDepartureDelay,
    removeArrivalDelay,
    replanFlight,
    updateDepartureDelay,
    updateArrivalDelay,
} from '../../infrastructure/flightRest';
import { ResponseError } from '../../infrastructure/restUtils';
import { actualTimeRefusalText } from './actualTimeText';
import { confirmDialog } from '../confirmDialog';
import { clearError, showError } from '../messagePresenter';
import { replannedText, replanningOffer, replanReason, replanSteps } from './replanning';
import { brusselsToInstant, formatBrusselsDateTime } from '../timeFormat';
import {
    displayActualTimes,
    displayActualTimeForms,
    hideActualTimeForms,
    initializeActualTimes,
    readActualTime,
    setActualTimesBusy,
} from './flightActualTimesPresenter';
import {
    clearDelays,
    displayDelayTables,
    initializeDelays,
    readDelayInput,
    reasonText,
    resetDelayForm,
    setDelaysBusy,
    showDelayEditor,
} from './flightDelaysPresenter';
import { clearHistory, displayHistory } from './flightHistoryPresenter';
import { initializeUser } from '../userPresenter';

const content = document.querySelector<HTMLElement>('#content')!;
const pageStatus = document.querySelector<HTMLElement>('#page-status')!;
const refreshButton = document.querySelector<HTMLButtonElement>('#refresh')!;
const permissionsMessage = document.querySelector<HTMLElement>('#permissions')!;
let flightKey: FlightKey;
let currentFlight: Flight | null = null;
let delayReasons: DelayReason[] = [];
let airports: Airport[] = [];
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
    setDelaysBusy(busy);
    setActualTimesBusy(busy);
    refreshButton.disabled = busy;
}

function resetDelayEditor(): void {
    delayBeingEdited = null;
    resetDelayForm(delayReasons, canChangeDelay(true), canChangeDelay(false));
}

function editDelay(delay: Delay, departure: boolean): void {
    if (isBusy || !canChangeDelay(departure)) {
        return;
    }
    clearError();
    pageStatus.textContent = '';
    delayBeingEdited = delay;
    editingDeparture = departure;
    showDelayEditor(delay, departure);
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

function airportText(code: string): string {
    const airport = airports.find(airport => airport.code === code);
    return airport ? `${code} — ${airport.name}` : code;
}

function displayFlightDetails(flight: Flight): void {
    document.querySelector<HTMLElement>('#flight-number')!.textContent = flight.flightNumber;
    document.querySelector<HTMLElement>('#flight-date')!.textContent = flight.scheduledDepartureDate;
    document.querySelector<HTMLElement>('#flight-from')!.textContent = airportText(flight.departureAirportCode);
    document.querySelector<HTMLElement>('#flight-to')!.textContent = airportText(flight.arrivalAirportCode);
    const status = document.querySelector<HTMLElement>('#flight-status')!;
    status.textContent = flight.status;
    status.dataset.status = flight.status;
    document.querySelector<HTMLElement>('#scheduled-departure')!.textContent = formatBrusselsDateTime(flight.scheduledDeparture);
    document.querySelector<HTMLElement>('#scheduled-arrival')!.textContent = formatBrusselsDateTime(flight.scheduledArrival);
    displayEstimate('departure', flight.scheduledDeparture, flight.estimatedDeparture);
    displayEstimate('arrival', flight.scheduledArrival, flight.estimatedArrival);
    displayActualTimes(flight);
}

function displayPermissions(flight: Flight): void {
    if (flight.status === 'REPLANNED') {
        permissionsMessage.textContent = `${replannedText(flight)} Its delays are frozen.`;
    } else if (!hasRole('flight-planner')) {
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
    displayDelayTables(flight, delayReasons, canChangeDelay(true), canChangeDelay(false), isBusy);
    displayPermissions(flight);
    displayActualTimeForms(flight);
    resetDelayEditor();
    content.hidden = false;
    void displayHistory(flight);
}

async function refreshFlight(): Promise<Flight> {
    try {
        const flight = await getFlight(flightKey);
        displayFlight(flight);
        return flight;
    } catch (error) {
        currentFlight = null;
        clearDelays();
        hideActualTimeForms();
        clearHistory();
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
    if (isBusy) {
        return;
    }
    const input = readDelayInput();
    if (input === null) {
        return;
    }
    clearError();
    pageStatus.textContent = '';
    let departure = input.departure;
    if (delayBeingEdited !== null) {
        departure = editingDeparture;
    }
    const minutes = input.minutes;
    const description = input.description;
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
            const delay = { minutes, reasonCode: input.reasonCode, description };
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
        } else if (await offerReplanning(error, departure, minutes, description, input.reasonCode)) {
            // the refusal became a replanning so there is no error to show
        } else {
            showError(error);
            await reloadAfterRefusal(error);
        }
    } finally {
        setBusy(false);
    }
}

// a 409 over 12 hours asks the replan question at once, with the flight and times the server
// proposed; the server checks everything again on the replan request
async function offerReplanning(error: unknown, departure: boolean, minutes: number, description: string, reasonCode: string): Promise<boolean> {
    if (!(error instanceof ResponseError) || currentFlight === null) {
        return false;
    }
    const offer = replanningOffer(error.problem, hasRole('flight-planner'));
    if (offer === null) {
        return false;
    }
    const confirmed = await confirmDialog({
        title: 'Replan this flight?',
        message: replanReason(offer),
        steps: replanSteps(currentFlight, offer),
        confirmLabel: 'Replan flight',
    });
    if (!confirmed) {
        showError(error);
        return true;
    }
    const request: ReplanFlightRequest = {
        delayType: departure ? 'DEPARTURE' : 'ARRIVAL',
        delayId: delayBeingEdited?.id ?? null,
        minutes,
        reasonCode: delayBeingEdited === null ? reasonCode : null,
        description,
    };
    try {
        displayFlight(await replanFlight(flightKey, request));
        pageStatus.textContent = 'Flight replanned. The new flight appears when the planning system has processed it.';
    } catch (replanError) {
        showError(replanError);
        await reloadAfterRefusal(replanError);
    }
    return true;
}

async function deleteDelay(delay: Delay, departure: boolean): Promise<void> {
    if (isBusy || !canChangeDelay(departure)) {
        return;
    }
    const confirmed = await confirmDialog({
        title: 'Delete this delay?',
        message: 'Partners will be notified of the new times.',
        details: [
            { label: 'Type', value: departure ? 'Departure' : 'Arrival' },
            { label: 'Minutes', value: String(delay.minutes) },
            { label: 'IATA reason', value: reasonText(delay.reasonCode, delayReasons) },
            { label: 'Description', value: delay.description },
        ],
        confirmLabel: 'Delete delay',
        danger: true,
    });
    if (!confirmed) {
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

// the page shows brussels time, so a refusal with a time in it gets the page's own text
function actualTimeError(error: unknown, entered: string | null, departure: boolean): unknown {
    if (!(error instanceof ResponseError) || entered === null) {
        return error;
    }
    const text = actualTimeRefusalText({
        refusal: error.problem.refusal,
        latestAllowed: error.problem.latestAllowed,
        departure,
        entered,
        actualDeparture: currentFlight?.actualDeparture ?? null,
        actualArrival: currentFlight?.actualArrival ?? null,
    });
    return text === null ? error : new Error(text);
}

async function registerActualTime(event: Event, departure: boolean): Promise<void> {
    event.preventDefault();
    if (isBusy || currentFlight === null) {
        return;
    }
    const input = readActualTime(departure);
    if (input === null) {
        return;
    }
    clearError();
    pageStatus.textContent = '';
    setBusy(true);
    let instant: string | null = null;
    try {
        instant = brusselsToInstant(input);
        const flight = departure ? await recordDeparture(flightKey, instant) : await recordArrival(flightKey, instant);
        displayFlight(flight);
        pageStatus.textContent = departure
            ? 'Departure registered. Partners get the AD message. Departure delays are now frozen.'
            : 'Arrival registered. Partners get the AA message. All delays are now frozen.';
    } catch (error) {
        // the reload shows the server text, so the brussels-time text comes after it
        await reloadAfterRefusal(error);
        showError(actualTimeError(error, instant, departure));
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
        initializeDelays(saveDelay, resetDelayEditor, editDelay, deleteDelay);
        initializeActualTimes(registerActualTime);
        document.querySelector<HTMLAnchorElement>('#back-to-flights')!.href = `../index.html?${new URLSearchParams({ date: scheduledDepartureDate })}`;
        pageStatus.textContent = 'Loading…';
        try {
            airports = await getAirports();
        } catch {
            // the page still works with only the airport codes
        }
        try {
            delayReasons = await getDelayReasons();
        } catch {
            showError(new Error('Delay reasons could not be loaded. Refresh the page before adding a delay.'));
        }
        await refreshFlight();
        pageStatus.textContent = '';
        refreshButton.addEventListener('click', handleRefreshClick);
    } catch (error) {
        pageStatus.textContent = '';
        showError(error);
    }
}

void initialize();
