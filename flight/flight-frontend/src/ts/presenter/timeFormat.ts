const DISPLAY_TIME_ZONE = 'Europe/Brussels';

const dateFormat = new Intl.DateTimeFormat('en-GB', {
    timeZone: DISPLAY_TIME_ZONE,
    dateStyle: 'short',
});
const timeFormat = new Intl.DateTimeFormat('en-GB', {
    timeZone: DISPLAY_TIME_ZONE,
    timeStyle: 'short',
});
// every part separately so the brussels wall time can be read back as numbers
const wallClockFormat = new Intl.DateTimeFormat('en-GB', {
    timeZone: DISPLAY_TIME_ZONE,
    hourCycle: 'h23',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
});
const HOUR = 60 * 60 * 1000;

// 2026-10-21T08:00:00Z becomes 21/10/2026 10:00 like the cargo pages
export function formatBrusselsDateTime(isoInstant: string): string {
    const instant = new Date(isoInstant);
    return `${dateFormat.format(instant)} ${timeFormat.format(instant)}`;
}

// the brussels wall time of an instant, written as if it were utc, so two wall times can be
// compared as numbers: 2026-11-10T20:03:00Z gives the number of 2026-11-10T21:03:00Z
function brusselsWallClock(instant: Date): number {
    const parts: Record<string, string> = {};
    for (const part of wallClockFormat.formatToParts(instant)) {
        parts[part.type] = part.value;
    }
    return Date.UTC(Number(parts.year), Number(parts.month) - 1, Number(parts.day), Number(parts.hour), Number(parts.minute));
}

// a datetime-local value in brussels time, like 2026-11-10T21:03, becomes the utc instant
// 2026-11-10T20:03:00.000Z; seconds are dropped, the page works in whole minutes
// dst rule (team reading of Q04): in the autumn overlap the earlier, summer-time instant is
// taken; in the spring gap the entered time does not exist and the instant one hour later is
// taken (02:30 becomes 03:30 summer time)
export function brusselsToInstant(local: string): string {
    const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(local);
    if (match === null) {
        throw new Error('Enter a date and a time.');
    }
    const [, year, month, day, hour, minute] = match;
    // the wall time as a utc number, the first guess for the instant
    const wanted = Date.UTC(Number(year), Number(month) - 1, Number(day), Number(hour), Number(minute));
    let instant = wanted;
    // shift the guess by what brussels shows for it, twice so a dst change in between is caught
    for (let round = 0; round < 2; round++) {
        instant += wanted - brusselsWallClock(new Date(instant));
    }
    // in the overlap an hour earlier shows the same wall time: take that earlier instant
    if (brusselsWallClock(new Date(instant - HOUR)) === wanted) {
        instant -= HOUR;
    }
    return new Date(instant).toISOString();
}

// now in brussels as a datetime-local value with whole minutes, the default of the inputs
export function brusselsNowForInput(now: Date = new Date()): string {
    return new Date(brusselsWallClock(now)).toISOString().slice(0, 16);
}
