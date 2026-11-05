const LIMIT = 100;
const KNOWN_TYPES = ['FLM', 'MVT_ED', 'MVT_EA', 'MVT_AD', 'MVT_AA', 'CPM'];

const typeFilter = document.querySelector('#type-filter');
const flightSearch = document.querySelector('#flight-search');
const refreshButton = document.querySelector('#refresh');
const status = document.querySelector('#status');
const messageList = document.querySelector('#messages');
const messageTemplate = document.querySelector('#message-template');
const detail = document.querySelector('#detail');
const detailEmpty = document.querySelector('#detail-empty');
const detailContent = document.querySelector('#detail-content');
const detailTitle = document.querySelector('#detail-title');
const detailPayload = document.querySelector('#detail-payload');
const detailState = document.querySelector('#detail-state');
const detailCreated = document.querySelector('#detail-created');
const detailVersion = document.querySelector('#detail-version');
const detailId = document.querySelector('#detail-id');

const brusselsFormat = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Europe/Brussels',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
});

let messages = [];
let selectedId = null;
let loadFailed = false;

// en-gb gives 05/11/2026, 14:30, so only the comma has to go
function brusselsTime(instant) {
    return brusselsFormat.format(new Date(instant)).replace(',', '');
}

// MVT_AD reads as MVT AD, and a type we do not know stays readable the same way
function typeLabel(type) {
    return type.replaceAll('_', ' ');
}

function typeBadge(type) {
    if (type === 'FLM') {
        return 'badge badge-blue';
    }
    if (type.startsWith('MVT')) {
        return 'badge badge-amber';
    }
    if (type === 'CPM') {
        return 'badge badge-green';
    }
    return 'badge badge-grey';
}

// an mvt is not json, so it falls through and keeps its three lines
function readablePayload(payload) {
    try {
        return JSON.stringify(JSON.parse(payload), null, 2);
    } catch {
        return payload;
    }
}

function matchesType(message) {
    if (typeFilter.value === 'ALL') {
        return true;
    }
    if (typeFilter.value === 'OTHER') {
        return !KNOWN_TYPES.includes(message.type);
    }
    return message.type === typeFilter.value;
}

function matchesFlight(message) {
    const search = flightSearch.value.trim().toUpperCase();
    return (message.flightNumber ?? '').toUpperCase().includes(search);
}

function statusText(visibleCount) {
    if (loadFailed) {
        return 'Could not reach the Partner service. Check that it is running, then press Refresh.';
    }
    if (messages.length === 0) {
        return 'No partner messages yet.';
    }
    if (visibleCount === 0) {
        return `None of the newest ${messages.length} messages match.`;
    }
    return `Showing ${visibleCount} of the newest ${messages.length} messages.`;
}

function renderDetail(message) {
    detailEmpty.hidden = message !== undefined;
    detailContent.hidden = message === undefined;
    if (message === undefined) {
        return;
    }
    const published = message.state === 'PUBLISHED';
    const label = typeLabel(message.type);
    detailTitle.textContent = message.flightNumber ? `${label} for ${message.flightNumber}` : label;
    detailPayload.textContent = readablePayload(message.payload);
    detailState.textContent = published ? 'Published' : 'Pending';
    detailState.className = published ? 'badge badge-green' : 'badge badge-amber';
    detailCreated.textContent = brusselsTime(message.createdAt);
    detailVersion.textContent = message.formatVersion;
    detailId.textContent = message.id;
}

// does not rebuild the list, so the clicked row keeps the keyboard focus
function select(message, button) {
    selectedId = message.id;
    messageList.querySelector('[aria-current]')?.removeAttribute('aria-current');
    button.setAttribute('aria-current', 'true');
    renderDetail(message);
    // on a phone the detail sits below the list
    detail.scrollIntoView({ block: 'nearest' });
}

function messageRow(message) {
    const row = messageTemplate.content.cloneNode(true);
    const button = row.querySelector('.message');
    const type = row.querySelector('.message-type');
    type.textContent = typeLabel(message.type);
    type.className = typeBadge(message.type);
    row.querySelector('.message-flight').textContent = message.flightNumber ?? 'Unknown flight';
    row.querySelector('.message-time').textContent = brusselsTime(message.createdAt);
    if (message.id === selectedId) {
        button.setAttribute('aria-current', 'true');
    }
    button.addEventListener('click', () => select(message, button));
    return row;
}

function render() {
    const visible = messages.filter(message => matchesType(message) && matchesFlight(message));
    messageList.replaceChildren(...visible.map(messageRow));
    status.textContent = statusText(visible.length);
    status.classList.toggle('status-error', loadFailed);
    renderDetail(visible.find(message => message.id === selectedId));
}

async function load() {
    refreshButton.disabled = true;
    try {
        const response = await fetch(`/api/partner-messages?limit=${LIMIT}`);
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }
        messages = await response.json();
        loadFailed = false;
    } catch {
        messages = [];
        loadFailed = true;
    }
    refreshButton.disabled = false;
    render();
}

typeFilter.addEventListener('change', render);
flightSearch.addEventListener('input', render);
refreshButton.addEventListener('click', load);
load();
