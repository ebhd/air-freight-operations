const dateTimeFormat = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Europe/Brussels',
    dateStyle: 'short',
    timeStyle: 'short',
});

export function formatBrusselsDateTime(isoInstant: string): string {
    return dateTimeFormat.format(new Date(isoInstant));
}
