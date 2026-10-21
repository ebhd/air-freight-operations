const DISPLAY_TIME_ZONE = 'Europe/Brussels';

// the flight key holds the utc departure date so today is the utc date as well
export function utcToday(): string {
    return new Date().toISOString().slice(0, 10);
}

const dateFormat = new Intl.DateTimeFormat('en-GB', {
    timeZone: DISPLAY_TIME_ZONE,
    dateStyle: 'short',
});
const timeFormat = new Intl.DateTimeFormat('en-GB', {
    timeZone: DISPLAY_TIME_ZONE,
    timeStyle: 'short',
});

// 2026-10-21T08:00:00Z becomes 21/10/2026 10:00
export function formatBrusselsDateTime(isoInstant: string): string {
    const instant = new Date(isoInstant);
    return `${dateFormat.format(instant)} ${timeFormat.format(instant)}`;
}
