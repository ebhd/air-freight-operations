import { describe, expect, it } from 'vitest';
import { tooLateText } from '../../ts/presenter/actualTimeText';

describe('tooLateText', () => {
    it('shows the limit in brussels time with the delay hint', () => {
        expect(tooLateText('2026-11-10T20:05:00Z')).toBe('The actual time may be at most 10/11/2026 21:05 (Brussels). Add a delay first, then register the time.');
    });
});
