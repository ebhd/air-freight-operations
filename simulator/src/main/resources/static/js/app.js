const TIME_ZONE = 'Europe/Brussels';
const ARRIVAL_AIRPORTS = ['AMS', 'CDG', 'FRA', 'LHR', 'MAD'];
// in the order of Date.getUTCDay, which starts on sunday
const WEEKDAYS = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];
const MINUTE = 60 * 1000;
const HOUR = 60 * MINUTE;
const STORAGE_KEY = 'simulator-flights';

const form = document.querySelector('#flight-form');
const fields = form.elements;
const uldCount = document.querySelector('#uld-count');
const flightList = document.querySelector('#flights');
const flightTemplate = document.querySelector('#flight-template');
const results = document.querySelector('#results');

// the flights created in this browser tab
const flights = JSON.parse(sessionStorage.getItem(STORAGE_KEY) ?? '[]');

// the addresses of the flight and cargo pages come from the simulator's spring settings
const pageUrls = await fetch('/api/simulator/page-urls').then(response => response.json());

function randomBetween(lowest, highest) {
    return lowest + Math.floor(Math.random() * (highest - lowest + 1));
}

// the swedish format is the only one that already looks like 2026-11-05 14:30:00
function brusselsText(date) {
    return date.toLocaleString('sv-SE', { timeZone: TIME_ZONE }).replace(' ', 'T');
}

// how far brussels runs ahead of utc at this moment
function brusselsOffset(date) {
    return new Date(`${brusselsText(date)}Z`) - date;
}

// a second look because the first guess can fall on the other side of a clock change
function brusselsToUtc(inputValue) {
    const wallClock = new Date(`${inputValue}Z`);
    const guess = new Date(wallClock - brusselsOffset(wallClock));
    return new Date(wallClock - brusselsOffset(guess));
}

function utcDate(date) {
    return date.toISOString().slice(0, 10);
}

function utcTime(date) {
    return date.toISOString().slice(11, 19);
}

function newFlightNumber() {
    return `SIM${randomBetween(1000, 9999)}`;
}

function fillForm(arrivalAirport, hoursUntilDeparture, flightMinutes) {
    const departure = new Date(Date.now() + hoursUntilDeparture * HOUR);
    const arrival = new Date(departure.getTime() + flightMinutes * MINUTE);
    fields.flightNumber.value = newFlightNumber();
    fields.arrivalAirportCode.value = arrivalAirport;
    fields.departure.value = brusselsText(departure).slice(0, 16);
    fields.arrival.value = brusselsText(arrival).slice(0, 16);
}

// a loading window is open from 12 hours before departure, so 3 hours from now is inside it
function fillDefaults() {
    fillForm('AMS', 3, 90);
}

function showResult(text, isError = false) {
    const result = document.createElement('li');
    result.className = isError ? 'result result-error' : 'result';
    result.textContent = text;
    results.prepend(result);
}

// gives the message ids, or throws an error with a text the page can show
async function post(path, body) {
    let response;
    try {
        response = await fetch(path, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
            body: JSON.stringify(body),
        });
    } catch {
        throw new Error('Not published: the simulator did not answer.');
    }
    const answer = await response.json().catch(() => ({}));
    if (!response.ok) {
        const reason = answer.detail ?? answer.error ?? 'no reason given';
        throw new Error(`Not published (HTTP ${response.status}): ${reason}`);
    }
    return answer.messageIds;
}

function flightPageQuery(flight) {
    return new URLSearchParams({
        flightNumber: flight.flightNumber,
        scheduledDepartureDate: flight.scheduledDepartureDate,
        departureAirportCode: flight.departureAirportCode,
    });
}

function renderFlights() {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(flights));
    flightList.replaceChildren();
    for (const flight of flights) {
        const row = flightTemplate.content.cloneNode(true);
        const query = flightPageQuery(flight);
        row.querySelector('.flight-title').textContent =
            `${flight.flightNumber} ${flight.departureAirportCode} to ${flight.arrivalAirportCode}`;
        row.querySelector('.flight-details').textContent =
            `Departs ${flight.departureInBrussels} Brussels time. ULDs sent: ${flight.uldsSent}`;
        row.querySelector('.flight-link').href =
            `${pageUrls.flightUrl}/html/flight-details.html?${query}`;
        row.querySelector('.cargo-link').href = `${pageUrls.cargoUrl}/cargo-plan.html?${query}`;
        row.querySelector('.add-ulds').addEventListener('click', event =>
            addUlds(flight, event.currentTarget),
        );
        flightList.append(row);
    }
}

async function createFlight(event) {
    event.preventDefault();
    const departure = brusselsToUtc(fields.departure.value);
    const arrival = brusselsToUtc(fields.arrival.value);
    // an ssm only has the arrival time, so flight reads it as the same day or the next day
    if (arrival <= departure || arrival - departure > 24 * HOUR) {
        showResult('Not sent: the arrival must be after the departure and within 24 hours.', true);
        return;
    }

    const flightNumber = fields.flightNumber.value.trim();
    const departureAirportCode = fields.departureAirportCode.value.toUpperCase();
    const arrivalAirportCode = fields.arrivalAirportCode.value.toUpperCase();
    const schedule = {
        startDate: utcDate(departure),
        endDate: utcDate(departure),
        operatingDays: [WEEKDAYS[departure.getUTCDay()]],
        departureTime: utcTime(departure),
        arrivalTime: utcTime(arrival),
        flightNumber,
        departureAirportCode,
        arrivalAirportCode,
        aircraftRegistration: fields.aircraftRegistration.value.trim(),
    };

    try {
        const messageIds = await post('/api/simulator/flight-schedules', [schedule]);
        flights.unshift({
            flightNumber,
            scheduledDepartureDate: schedule.startDate,
            departureAirportCode,
            arrivalAirportCode,
            departureInBrussels: fields.departure.value.replace('T', ' '),
            uldsSent: 0,
        });
        renderFlights();
        showResult(
            `Published flight ${flightNumber}, departure ${schedule.startDate}` +
                ` ${schedule.departureTime} UTC. Message ID: ${messageIds.join(', ')}`,
        );
        fields.flightNumber.value = newFlightNumber();
    } catch (error) {
        showResult(error.message, true);
    }
}

// numbered per flight, because cargo ignores a uld code that is already on the flight
function buildBookings(flight, count) {
    const bookings = [];
    for (let number = flight.uldsSent + 1; number <= flight.uldsSent + count; number++) {
        bookings.push({
            flightNumber: flight.flightNumber,
            scheduledDepartureDate: flight.scheduledDepartureDate,
            departureAirportCode: flight.departureAirportCode,
            uldCode: `AKE${String(number).padStart(5, '0')}KL`,
            weight: { value: randomBetween(200, 1500), unit: 'KG' },
            destination: flight.arrivalAirportCode,
        });
    }
    return bookings;
}

async function addUlds(flight, button) {
    if (!uldCount.reportValidity()) {
        return;
    }
    const bookings = buildBookings(flight, Number(uldCount.value));
    button.disabled = true;
    try {
        const messageIds = await post('/api/simulator/uld-bookings', bookings);
        flight.uldsSent += bookings.length;
        renderFlights();
        const uldCodes = bookings.map(booking => booking.uldCode).join(', ');
        showResult(
            `Published ${bookings.length} ULDs for ${flight.flightNumber}: ${uldCodes}.` +
                ` Message IDs: ${messageIds.join(', ')}`,
        );
    } catch (error) {
        showResult(error.message, true);
    } finally {
        button.disabled = false;
    }
}

function createRandomFlight() {
    const airport = ARRIVAL_AIRPORTS[randomBetween(0, ARRIVAL_AIRPORTS.length - 1)];
    fillForm(airport, randomBetween(2, 8), randomBetween(60, 120));
    form.requestSubmit();
}

form.addEventListener('submit', createFlight);
document.querySelector('#random-flight').addEventListener('click', createRandomFlight);
fillDefaults();
renderFlights();
