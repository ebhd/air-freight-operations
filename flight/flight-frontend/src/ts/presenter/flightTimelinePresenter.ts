import type { FlightOverview, FlightTimeline } from '../domain/Flight';
import { flightDetailsPage, getFlightTimeline } from '../infrastructure/flightRest';
import { clearError, showError } from './messagePresenter';
import { formatBrusselsDateTime } from './timeFormat';
import { initializeUser } from './userPresenter';

const dateInput = document.querySelector<HTMLInputElement>('#date')!;
const refreshButton = document.querySelector<HTMLButtonElement>('#refresh')!;
const chart = document.querySelector<HTMLElement>('#timeline')!;
const timelineStatus = document.querySelector<HTMLElement>('#timeline-status')!;
const hour = 60 * 60 * 1000;
const flightTimeFormat = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Europe/Brussels', hour: '2-digit', minute: '2-digit',
});
const detailFormat = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Europe/Brussels', day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit', timeZoneName: 'shortOffset',
});
const axisFormat = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Europe/Brussels', hour: '2-digit', minute: '2-digit',
});
let latestRequestId = 0;
let dayStart = 0;
let dayEnd = 0;

async function showTimeline(): Promise<void> {
    latestRequestId += 1;
    const requestId = latestRequestId;
    clearError();
    chart.replaceChildren();
    if (!dateInput.value || !dateInput.validity.valid) {
        timelineStatus.textContent = '';
        refreshButton.disabled = false;
        showError(new Error('Choose a valid date.'));
        return;
    }
    timelineStatus.textContent = 'Loading flights...';
    refreshButton.disabled = true;
    try {
        const timeline = await getFlightTimeline(dateInput.value);
        if (requestId !== latestRequestId) {
            return;
        }
        displayTimeline(timeline);
        history.replaceState(null, '', `?date=${dateInput.value}`);
    } catch (error) {
        if (requestId === latestRequestId) {
            timelineStatus.textContent = '';
            showError(error);
        }
    } finally {
        if (requestId === latestRequestId) {
            refreshButton.disabled = false;
        }
    }
}

function displayTimeline(timeline: FlightTimeline): void {
    dayStart = Date.parse(timeline.start);
    dayEnd = Date.parse(timeline.end);
    const hours = (dayEnd - dayStart) / hour;
    chart.style.setProperty('--hour-width', `${100 / hours}%`);
    const header = document.createElement('div');
    header.className = 'timeline-row timeline-header';
    const heading = document.createElement('div');
    heading.className = 'timeline-label';
    heading.textContent = 'Flight / route';
    const axis = document.createElement('div');
    axis.className = 'timeline-axis';
    for (let time = dayStart; time <= dayEnd - 3 * hour; time += 3 * hour) {
        axis.append(createTick(time));
    }
    const endTick = createTick(dayEnd);
    endTick.classList.add('timeline-tick-end');
    endTick.textContent = '00:00 (+1 day)';
    axis.append(endTick);
    header.append(heading, axis);
    chart.append(header);
    for (const flight of timeline.flights) {
        chart.append(createFlightRow(flight));
    }
    timelineStatus.textContent = `Flights shown: ${timeline.flights.length}. Brussels day ${dateInput.value} (${hours} hours).`;
    if (timeline.flights.length === 0) {
        timelineStatus.textContent = `No flights on ${dateInput.value} in Brussels.`;
    }
}

function createTick(time: number): HTMLElement {
    const tick = document.createElement('span');
    tick.className = 'timeline-tick';
    tick.style.left = `${100 * (time - dayStart) / (dayEnd - dayStart)}%`;
    tick.textContent = axisFormat.format(new Date(time));
    if (time === dayStart) {
        tick.classList.add('timeline-tick-start');
    }
    return tick;
}

function createFlightRow(flight: FlightOverview): HTMLElement {
    const row = document.createElement('div');
    row.className = 'timeline-row';
    const label = document.createElement('div');
    label.className = 'timeline-label';
    const number = document.createElement('strong');
    number.textContent = flight.flightNumber;
    const route = document.createElement('div');
    route.textContent = `${flight.departureAirportCode} to ${flight.arrivalAirportCode}`;
    const scheduled = document.createElement('div');
    scheduled.className = 'timeline-times';
    scheduled.textContent = `Scheduled: ${formatFlightTime(flight.scheduledDeparture)} - ${formatFlightTime(flight.scheduledArrival)}`;
    label.append(number, route, scheduled);
    const track = document.createElement('div');
    track.className = 'timeline-track';
    addBar(track, flight);
    const delayed = Date.parse(flight.estimatedDeparture) !== Date.parse(flight.scheduledDeparture)
        || Date.parse(flight.estimatedArrival) !== Date.parse(flight.scheduledArrival);
    if (delayed) {
        const expected = document.createElement('div');
        expected.className = 'timeline-times timeline-delay';
        expected.textContent = `Expected: ${formatFlightTime(flight.estimatedDeparture)} - ${formatFlightTime(flight.estimatedArrival)}`;
        label.append(expected);
    }
    row.append(label, track);
    return row;
}

function formatFlightTime(instant: string): string {
    const time = Date.parse(instant);
    if (time < dayStart || time >= dayEnd) {
        return formatBrusselsDateTime(instant);
    }
    return flightTimeFormat.format(new Date(time));
}

function addBar(track: HTMLElement, flight: FlightOverview): void {
    const start = Date.parse(flight.scheduledDeparture);
    const scheduledEnd = Date.parse(flight.scheduledArrival);
    const end = Date.parse(flight.estimatedArrival);
    if (start >= dayEnd || end <= dayStart) {
        return;
    }
    const visibleStart = Math.max(start, dayStart);
    const visibleEnd = Math.min(end, dayEnd);
    const visibleScheduledEnd = Math.min(Math.max(scheduledEnd, visibleStart), visibleEnd);
    const bar = document.createElement('a');
    bar.className = 'timeline-bar';
    bar.href = flightDetailsPage({
        flightNumber: flight.flightNumber,
        scheduledDepartureDate: flight.scheduledDepartureDate,
        departureAirportCode: flight.departureAirportCode,
    });
    bar.style.left = `${100 * (visibleStart - dayStart) / (dayEnd - dayStart)}%`;
    bar.style.width = `${100 * (visibleEnd - visibleStart) / (dayEnd - dayStart)}%`;
    bar.style.setProperty('--scheduled-width', `${100 * (visibleScheduledEnd - visibleStart) / (visibleEnd - visibleStart)}%`);
    let description = `${flight.flightNumber} · Scheduled: ${detailFormat.format(new Date(start))} - ${detailFormat.format(new Date(scheduledEnd))}`;
    if (Date.parse(flight.estimatedDeparture) !== start || end !== scheduledEnd) {
        description += ` · Expected: ${detailFormat.format(new Date(flight.estimatedDeparture))} - ${detailFormat.format(new Date(end))}`;
    }
    bar.setAttribute('aria-label', description);
    if (start < dayStart) {
        bar.classList.add('timeline-continues-before');
    }
    if (end > dayEnd) {
        bar.classList.add('timeline-continues-after');
    }
    const tooltip = document.createElement('span');
    tooltip.className = 'timeline-tooltip';
    tooltip.textContent = description;
    tooltip.setAttribute('aria-hidden', 'true');
    track.append(bar, tooltip);
}

async function initialize(): Promise<void> {
    try {
        await initializeUser();
        const today = new Intl.DateTimeFormat('sv-SE', {
            timeZone: 'Europe/Brussels', year: 'numeric', month: '2-digit', day: '2-digit',
        }).format(new Date());
        const query = new URLSearchParams(window.location.search);
        dateInput.value = query.get('date') ?? today;
        dateInput.addEventListener('change', showTimeline);
        refreshButton.addEventListener('click', showTimeline);
        document.querySelector<HTMLElement>('#content')!.hidden = false;
        await showTimeline();
    } catch (error) {
        showError(error);
    }
}

void initialize();
