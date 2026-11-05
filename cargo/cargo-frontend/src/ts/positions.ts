import { getJson, sendJson, type ApiResult } from './api.js';
import type { UldBookingResponse } from './uld-booking.js';

// one main-deck position as sent by LoadingPositionResponse
export type LoadingPositionResponse = {
    code: string;
    row: string;
    // none for the single positions a1 a2 b1 and x
    side: 'LEFT' | 'RIGHT' | 'NONE';
};

// the list never changes so the page keeps the first successful answer
let positionList: LoadingPositionResponse[] | null = null;

// the server owns the list so the page never hard codes the 34 positions
export async function loadPositions(): Promise<ApiResult<LoadingPositionResponse[]>> {
    if (positionList !== null) {
        return { ok: true, data: positionList };
    }
    const result = await getJson<LoadingPositionResponse[]>('/api/loading-positions');
    if (result.ok) {
        positionList = result.data;
    }
    return result;
}

// code with row and side or single for a1 a2 b1 and x
function positionLabel(position: LoadingPositionResponse): string {
    if (position.side === 'NONE') {
        return `${position.code} - row ${position.row}, single`;
    }
    return `${position.code} - row ${position.row}, ${position.side.toLowerCase()}`;
}

// position code to the uld on it
export function takenPositions(bookings: UldBookingResponse[]): Map<string, string> {
    const taken = new Map<string, string>();
    for (const booking of bookings) {
        if (booking.position !== null) {
            taken.set(booking.position, booking.uldCode);
        }
    }
    return taken;
}

export function renderPositionSelect(
    booking: UldBookingResponse,
    positions: LoadingPositionResponse[],
    taken: Map<string, string>,
): HTMLSelectElement {
    const select = document.createElement('select');
    select.className = 'field h-9 w-full max-w-64';
    select.setAttribute('aria-label', `Position of ${booking.uldCode}`);
    // new Option sets plain text and never html
    select.append(new Option('No position', ''));
    for (const position of positions) {
        select.append(positionOption(position, booking.uldCode, taken));
    }
    select.value = booking.position ?? '';
    return select;
}

// a position of another uld is disabled only as a hint because the server decides
function positionOption(
    position: LoadingPositionResponse,
    uldCode: string,
    taken: Map<string, string>,
): HTMLOptionElement {
    const occupant = taken.get(position.code);
    if (occupant === undefined || occupant === uldCode) {
        return new Option(positionLabel(position), position.code);
    }
    const option = new Option(`${positionLabel(position)} (taken by ${occupant})`, position.code);
    option.disabled = true;
    return option;
}

// an empty code clears the position
export function changePosition(cargoPlanPath: string, uldCode: string, code: string): Promise<ApiResult<null>> {
    const path = `${cargoPlanPath}/bookings/${encodeURIComponent(uldCode)}/position`;
    if (code === '') {
        return sendJson('DELETE', path);
    }
    return sendJson('PUT', path, { position: code });
}
