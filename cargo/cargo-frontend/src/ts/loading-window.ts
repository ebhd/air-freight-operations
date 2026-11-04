import type { LoadingRefusal } from './api-failure.js';
import { getJson, type ApiResult } from './api.js';
import { refusalText } from './refusal-text.js';
import { formatBrusselsDateTime } from './time.js';

// the loading window of one flight as sent by LoadingWindowResponse
export type LoadingWindowStatus = {
    flightNumber: string;
    scheduledDepartureDate: string;
    departureAirportCode: string;
    flightStatus: string;
    opensAt: string;
    closesAt: string;
    finalized: boolean;
    checkedAt: string;
    loadingAllowed: boolean;
    // null when loading is allowed
    refusal: LoadingRefusal | null;
};

// the loading window belongs to the cargo plan so its path continues the plan path
export function loadLoadingWindow(cargoPlanPath: string): Promise<ApiResult<LoadingWindowStatus>> {
    return getJson<LoadingWindowStatus>(`${cargoPlanPath}/loading-window`);
}

// loadingAllowed lets later pages disable their controls but the server checks every action
export function renderLoadingWindow(element: HTMLDListElement, status: LoadingWindowStatus): void {
    element.replaceChildren(
        term('Opens (Brussels)'),
        detail(formatBrusselsDateTime(status.opensAt)),
        term('Closes (Brussels)'),
        detail(formatBrusselsDateTime(status.closesAt)),
        term('Loading'),
        detail(refusalText(status.refusal, status.opensAt, status.closesAt)),
    );
    element.hidden = false;
}

function term(text: string): HTMLElement {
    const dt = document.createElement('dt');
    dt.className = 'font-semibold';
    dt.append(text);
    return dt;
}

// append adds text as plain text and never as html
function detail(text: string): HTMLElement {
    const dd = document.createElement('dd');
    dd.append(text);
    return dd;
}
