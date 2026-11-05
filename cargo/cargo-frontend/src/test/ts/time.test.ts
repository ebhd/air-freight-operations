import { afterEach, describe, expect, it, vi } from 'vitest';
import { formatBrusselsDateTime, utcToday } from '../../ts/shared/time.js';

describe('formatBrusselsDateTime', () => {
    it.each([
        ['summer time is utc plus two', '2026-10-21T08:00:00Z', '21/10/2026 10:00'],
        ['winter time is utc plus one', '2026-10-26T08:00:00Z', '26/10/2026 09:00'],
        ['the last minute before the clocks go forward', '2026-03-29T00:59:00Z', '29/03/2026 01:59'],
        ['the first minute after the clocks go forward', '2026-03-29T01:00:00Z', '29/03/2026 03:00'],
    ])('%s', (_case, instant, text) => {
        expect(formatBrusselsDateTime(instant)).toBe(text);
    });

    it('shows the next date for an arrival after midnight', () => {
        expect(formatBrusselsDateTime('2026-10-21T21:30:00Z')).toBe('21/10/2026 23:30');
        expect(formatBrusselsDateTime('2026-10-21T23:10:00Z')).toBe('22/10/2026 01:10');
    });

    // the flight key keeps 21 october because that is the utc date of departure
    it('can show another date than the utc date of the flight key', () => {
        expect(formatBrusselsDateTime('2026-10-21T22:30:00Z')).toBe('22/10/2026 00:30');
    });

    // both 02:30 hours look the same because no offset is shown
    it('shows the repeated hour on 25 october twice as the same text', () => {
        expect(formatBrusselsDateTime('2026-10-25T00:30:00Z')).toBe('25/10/2026 02:30');
        expect(formatBrusselsDateTime('2026-10-25T01:30:00Z')).toBe('25/10/2026 02:30');
    });
});

describe('utcToday', () => {
    afterEach(() => {
        vi.useRealTimers();
    });

    // brussels is already on 22 october but the flight key date is still 21 october
    it('gives the utc date also when brussels is already on the next day', () => {
        vi.useFakeTimers();
        vi.setSystemTime(new Date('2026-10-21T23:30:00Z'));

        expect(utcToday()).toBe('2026-10-21');
    });
});
