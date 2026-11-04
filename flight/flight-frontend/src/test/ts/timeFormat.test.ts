import { describe, expect, it } from 'vitest';
import { brusselsNowForInput, brusselsToInstant, formatBrusselsDateTime } from '../../ts/presenter/timeFormat';

describe('formatBrusselsDateTime', () => {
    it.each([
        ['summer time is utc plus two', '2026-10-21T08:00:00Z', '21/10/2026 10:00'],
        ['winter time is utc plus one', '2026-10-26T08:00:00Z', '26/10/2026 09:00'],
        ['an arrival after midnight shows the next date', '2026-10-21T23:10:00Z', '22/10/2026 01:10'],
    ])('%s', (_case, instant, text) => {
        expect(formatBrusselsDateTime(instant)).toBe(text);
    });
});

describe('brusselsToInstant', () => {
    it.each([
        ['winter time is utc plus one', '2026-11-10T21:03', '2026-11-10T20:03:00.000Z'],
        ['summer time is utc plus two', '2026-07-10T21:03', '2026-07-10T19:03:00.000Z'],
        ['a brussels time after midnight is the previous utc day', '2026-11-11T00:30', '2026-11-10T23:30:00.000Z'],
        ['seconds are dropped', '2026-11-10T21:03:45', '2026-11-10T20:03:00.000Z'],
        ['the autumn overlap takes the earlier summer-time instant', '2026-10-25T02:30', '2026-10-25T00:30:00.000Z'],
        ['just after the overlap is winter time', '2026-10-25T03:30', '2026-10-25T02:30:00.000Z'],
        ['the spring gap takes the instant one hour later', '2026-03-29T02:30', '2026-03-29T01:30:00.000Z'],
        ['just after the gap is summer time', '2026-03-29T03:30', '2026-03-29T01:30:00.000Z'],
    ])('%s', (_case, local, instant) => {
        expect(brusselsToInstant(local)).toBe(instant);
    });

    it('round trips with the display format', () => {
        expect(formatBrusselsDateTime(brusselsToInstant('2026-11-10T21:03'))).toBe('10/11/2026 21:03');
    });

    it('refuses an empty or malformed value', () => {
        expect(() => brusselsToInstant('')).toThrow('Enter a date and a time.');
        expect(() => brusselsToInstant('21:03')).toThrow('Enter a date and a time.');
    });
});

describe('brusselsNowForInput', () => {
    it('gives the brussels wall time with whole minutes', () => {
        expect(brusselsNowForInput(new Date('2026-11-10T20:03:45Z'))).toBe('2026-11-10T21:03');
        expect(brusselsNowForInput(new Date('2026-07-10T23:30:00Z'))).toBe('2026-07-11T01:30');
    });
});
