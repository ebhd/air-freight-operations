import { beforeEach, describe, expect, it, vi } from 'vitest';
import { failureFromProblem } from '../../ts/shared/api-failure.js';
import { getJson, postJson, sendJson } from '../../ts/shared/api.js';
import { authorizedFetch } from '../../ts/shared/auth.js';

vi.mock('../../ts/shared/auth.js', () => ({
    AuthenticationError: class extends Error {},
    authorizedFetch: vi.fn(),
}));

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

    // the body that CargoApiExceptionHandler sends when a hard check blocks finalizing
    it('reads the report of a refused finalize', () => {
        const report = {
            hardChecksPassed: false,
            results: [{ name: 'Lateral balance', severity: 'HARD' as const, passed: false, reason: 'Right side is too heavy' }],
        };
        const failure = failureFromProblem(409, {
            detail: 'Load of flight HJ221/2026-10-22/BRU is not finalized: a hard check failed',
            refusal: 'HARD_CHECKS_FAILED',
            report,
        });

        expect(failure.loadCheckReport).toEqual(report);
        expect(failure.loadingRefusal).toBeUndefined();
    });

    it('names the status when there is no problem body', () => {
        expect(failureFromProblem(500, null).message).toBe('Request failed (HTTP 500)');
    });
});

describe('JSON responses', () => {
    beforeEach(() => vi.clearAllMocks());

    it.each(['GET', 'POST'])('reads a valid %s response', async (method) => {
        vi.mocked(authorizedFetch).mockResolvedValue(Response.json({ state: 'FINALIZED' }));

        const result = await (method === 'GET' ? getJson('/api/example') : postJson('/api/example'));

        expect(result).toEqual({ ok: true, data: { state: 'FINALIZED' } });
        expect(authorizedFetch).toHaveBeenCalledWith('/api/example', method === 'GET' ? {} : { method: 'POST' });
    });

    it.each(['GET', 'POST'])('reports unreadable JSON in a successful %s response', async (method) => {
        vi.mocked(authorizedFetch).mockResolvedValue(new Response('unreadable response', { status: 200 }));

        const result = await (method === 'GET' ? getJson('/api/example') : postJson('/api/example'));

        expect(result).toEqual({
            ok: false,
            status: 200,
            message: 'Cargo service returned an unreadable response. Reload the page.',
        });
    });

    it.each(['GET', 'POST'])('reports an interrupted %s response body', async (method) => {
        const response = Response.json({ state: 'FINALIZED' });
        vi.spyOn(response, 'json').mockRejectedValue(new TypeError('Body stream interrupted'));
        vi.mocked(authorizedFetch).mockResolvedValue(response);

        const result = await (method === 'GET' ? getJson('/api/example') : postJson('/api/example'));

        expect(result.ok).toBe(false);
        if (!result.ok) {
            expect(result.message).toContain('unreadable response');
        }
    });

    it('keeps the HTTP failure when its problem body is unreadable', async () => {
        vi.mocked(authorizedFetch).mockResolvedValue(new Response('unreadable problem', {
            status: 409,
            headers: { 'Content-Type': 'application/problem+json' },
        }));

        expect(await sendJson('PUT', '/api/example', { position: 'CL' })).toEqual({
            ok: false,
            status: 409,
            message: 'Request failed (HTTP 409)',
        });
    });
});
