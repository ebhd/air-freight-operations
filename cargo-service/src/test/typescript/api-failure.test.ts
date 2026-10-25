import { describe, expect, it } from 'vitest';
import { failureFromProblem } from '../../main/typescript/api-failure.js';

describe('failureFromProblem', () => {
    // the body that CargoApiExceptionHandler sends for a refused loading action
    it('reads the loading refusal of a 409', () => {
        const failure = failureFromProblem(409, {
            detail: 'Loading on HJ221/2026-10-22/BRU is not allowed: the loading window closed at 2026-10-22T07:40:00Z',
            refusal: 'WINDOW_CLOSED',
            opensAt: '2026-10-21T20:00:00Z',
            closesAt: '2026-10-22T07:40:00Z',
        });

        expect(failure.status).toBe(409);
        expect(failure.loadingRefusal).toEqual({
            refusal: 'WINDOW_CLOSED',
            opensAt: '2026-10-21T20:00:00Z',
            closesAt: '2026-10-22T07:40:00Z',
        });
    });

    it('keeps the detail of a problem without a loading refusal', () => {
        const failure = failureFromProblem(409, { detail: 'The cargo plan was changed by someone else' });

        expect(failure.message).toBe('The cargo plan was changed by someone else');
        expect(failure.loadingRefusal).toBeUndefined();
    });

    it('ignores half a loading refusal', () => {
        const failure = failureFromProblem(409, {
            detail: 'Refused',
            refusal: 'WINDOW_CLOSED',
            opensAt: '2026-10-21T20:00:00Z',
        });

        expect(failure.loadingRefusal).toBeUndefined();
    });

    it('names the status when there is no problem body', () => {
        expect(failureFromProblem(500, null).message).toBe('Request failed (HTTP 500)');
    });
});
