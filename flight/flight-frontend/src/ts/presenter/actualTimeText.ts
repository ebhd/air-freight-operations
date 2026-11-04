import { formatBrusselsDateTime } from './timeFormat';

// the one refusal with a time in it, shown in brussels time instead of the server's utc text
export function tooLateText(latestAllowed: string): string {
    return `The actual time may be at most ${formatBrusselsDateTime(latestAllowed)} (Brussels). Add a delay first, then register the time.`;
}
