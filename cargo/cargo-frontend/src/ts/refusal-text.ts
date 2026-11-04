import type { ApiFailure, LoadingRefusal } from './api-failure.js';
import { formatBrusselsDateTime } from './time.js';

export const LOAD_PLANNER_ONLY = 'Only a load planner can change positions.';

// one text per refusal code from the server with the times in brussels time
export function refusalText(refusal: LoadingRefusal | null, opensAt: string, closesAt: string): string {
    switch (refusal) {
        case null:
            return `Open until ${formatBrusselsDateTime(closesAt)}`;
        case 'WINDOW_NOT_OPEN_YET':
            return `Not open yet: the loading window opens at ${formatBrusselsDateTime(opensAt)}`;
        case 'WINDOW_CLOSED':
            return `Closed: the loading window closed at ${formatBrusselsDateTime(closesAt)}`;
        case 'FLIGHT_NOT_PLANNED':
            return 'Closed: the flight is no longer planned';
        case 'PLAN_FINALIZED':
            return 'Closed: the cargo plan is finalized';
        default: {
            // a missing case does not compile and a newer code from the server still gets a text
            const unknownRefusal: never = refusal;
            return `Closed (${unknownRefusal})`;
        }
    }
}

// the server text of a loading refusal holds utc times so the page writes its own
export function positionNotChangedText(uldCode: string, failure: ApiFailure): string {
    // the page knows the action so it can name the role that is needed
    if (failure.status === 403) {
        return `Position of ${uldCode} not changed: ${LOAD_PLANNER_ONLY}`;
    }
    if (failure.loadingRefusal !== undefined) {
        const { refusal, opensAt, closesAt } = failure.loadingRefusal;
        return `Position of ${uldCode} not changed: ${refusalText(refusal, opensAt, closesAt)}`;
    }
    return `Position of ${uldCode} not changed: ${failure.message}`;
}
