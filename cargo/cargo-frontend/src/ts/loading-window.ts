import { getJson, type ApiResult } from './api.js';
import { formatBrusselsDateTime } from './time.js';

// the codes of the java enum LoadingRefusal
type LoadingRefusal = 'PLAN_FINALIZED' | 'FLIGHT_NOT_PLANNED' | 'WINDOW_NOT_OPEN_YET' | 'WINDOW_CLOSED';

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
        detail(loadingText(status)),
    );
    element.hidden = false;
}

// one text per refusal code from the server
function loadingText(status: LoadingWindowStatus): string {
    switch (status.refusal) {
        case null:
            return `Open until ${formatBrusselsDateTime(status.closesAt)}`;
        case 'WINDOW_NOT_OPEN_YET':
            return `Not open yet: the loading window opens at ${formatBrusselsDateTime(status.opensAt)}`;
        case 'WINDOW_CLOSED':
            return `Closed: the loading window closed at ${formatBrusselsDateTime(status.closesAt)}`;
        case 'FLIGHT_NOT_PLANNED':
            return 'Closed: the flight is no longer planned';
        case 'PLAN_FINALIZED':
            return 'Closed: the cargo plan is finalized';
        default: {
            // a missing case does not compile and a newer code from the server still gets a text
            const unknownRefusal: never = status.refusal;
            return `Closed (${unknownRefusal})`;
        }
    }
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
