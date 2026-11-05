import { formatBrusselsDateTime } from '../timeFormat';

export type RefusedActualTime = {
    refusal?: string;
    latestAllowed?: string;
    departure: boolean;
    entered: string;
    actualDeparture: string | null;
    actualArrival: string | null;
};

function brussels(instant: string): string {
    return `${formatBrusselsDateTime(instant)} (Brussels)`;
}

export function tooLateText(latestAllowed: string): string {
    return `The actual time may be at most ${brussels(latestAllowed)}. Add a delay first, then register the time.`;
}

// the refusals with a time in them, shown in brussels time instead of the server's utc text
// null for the others, their server text has no time and can be shown as it is
export function actualTimeRefusalText(refused: RefusedActualTime): string | null {
    const name = refused.departure ? 'departure' : 'arrival';
    const registered = refused.departure ? refused.actualDeparture : refused.actualArrival;
    switch (refused.refusal) {
        case 'TOO_LATE':
            return refused.latestAllowed ? tooLateText(refused.latestAllowed) : null;
        case 'IN_THE_FUTURE':
            return `The actual ${name} ${brussels(refused.entered)} is in the future. An actual time can be at most now.`;
        case 'NOT_AFTER_DEPARTURE':
            return refused.actualDeparture
                ? `The actual arrival ${brussels(refused.entered)} must be later than the actual departure ${brussels(refused.actualDeparture)}.`
                : null;
        case 'NOT_PLANNED':
        case 'NOT_DEPARTED':
            return registered
                ? `The actual ${name} of this flight was already registered on ${brussels(registered)} and cannot be changed.`
                : null;
        default:
            return null;
    }
}
