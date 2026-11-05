const DISPLAY_TIME_ZONE = 'Europe/Brussels';

const dateFormat = new Intl.DateTimeFormat('en-GB', {
    timeZone: DISPLAY_TIME_ZONE,
    dateStyle: 'short',
});
const timeFormat = new Intl.DateTimeFormat('en-GB', {
    timeZone: DISPLAY_TIME_ZONE,
    timeStyle: 'short',
});

// 2026-10-21T08:00:00Z becomes 21/10/2026 10:00 like the cargo pages
export function formatBrusselsDateTime(isoInstant: string): string {
    const instant = new Date(isoInstant);
    return `${dateFormat.format(instant)} ${timeFormat.format(instant)}`;
}
