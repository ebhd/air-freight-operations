import { describe, expect, it } from 'vitest';
import { outcomeText, severityText, type LoadCheckResult } from '../../ts/load-check.js';

function result(severity: 'HARD' | 'SOFT', passed: boolean): LoadCheckResult {
    return { name: 'Lateral balance', severity, passed, reason: 'reason' };
}

describe('load check texts', () => {
    it.each([
        ['HARD', true, 'Passed'],
        ['HARD', false, 'Failed'],
        ['SOFT', true, 'Passed'],
        ['SOFT', false, 'Warning'],
    ] as const)('%s passed=%s gives "%s"', (severity, passed, text) => {
        expect(outcomeText(result(severity, passed))).toBe(text);
    });

    it('names the severity', () => {
        expect(severityText(result('HARD', true))).toBe('Hard');
        expect(severityText(result('SOFT', true))).toBe('Soft');
    });
});
