import { describe, expect, it } from 'vitest';
import { formatBrusselsDateTime } from '../../ts/presenter/timeFormat';

describe('formatBrusselsDateTime', () => {
    it.each([
        ['summer time is utc plus two', '2026-10-21T08:00:00Z', '21/10/2026 10:00'],
        ['winter time is utc plus one', '2026-10-26T08:00:00Z', '26/10/2026 09:00'],
        ['an arrival after midnight shows the next date', '2026-10-21T23:10:00Z', '22/10/2026 01:10'],
    ])('%s', (_case, instant, text) => {
        expect(formatBrusselsDateTime(instant)).toBe(text);
    });
});
