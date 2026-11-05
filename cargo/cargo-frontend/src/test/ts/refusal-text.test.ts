import { describe, expect, it } from 'vitest';
import type { ApiFailure } from '../../ts/shared/api-failure.js';
import { loadNotCheckedText, loadNotFinalizedText, positionNotChangedText, refusalText } from '../../ts/cargo/refusal-text.js';

// 20:00 and 07:40 utc are 22:00 and 09:40 in brussels summer time
const OPENS_AT = '2026-10-21T20:00:00Z';
const CLOSES_AT = '2026-10-22T07:40:00Z';

describe('refusalText', () => {
    it.each([
        [null, 'Open until 22/10/2026 09:40'],
        ['WINDOW_NOT_OPEN_YET', 'Not open yet: the loading window opens at 21/10/2026 22:00'],
        ['WINDOW_CLOSED', 'Closed: the loading window closed at 22/10/2026 09:40'],
        ['FLIGHT_NOT_PLANNED', 'Closed: the flight is no longer planned'],
        ['PLAN_FINALIZED', 'Closed: the cargo plan is finalized'],
    ] as const)('%s gives "%s"', (refusal, text) => {
        expect(refusalText(refusal, OPENS_AT, CLOSES_AT)).toBe(text);
    });

    it('uses winter time after the clocks go back on 25 October', () => {
        expect(refusalText('WINDOW_CLOSED', OPENS_AT, '2026-10-26T07:40:00Z')).toBe(
            'Closed: the loading window closed at 26/10/2026 08:40',
        );
    });
});

describe('positionNotChangedText', () => {
    it('writes a loading refusal in brussels time instead of the utc server text', () => {
        const failure: ApiFailure = {
            ok: false,
            status: 409,
            message: 'Loading on HJ221/2026-10-22/BRU is not allowed: the loading window closed at 2026-10-22T07:40:00Z',
            loadingRefusal: { refusal: 'WINDOW_CLOSED', opensAt: OPENS_AT, closesAt: CLOSES_AT },
        };

        expect(positionNotChangedText('AKE12345KL', failure)).toBe(
            'Position of AKE12345KL not changed: Closed: the loading window closed at 22/10/2026 09:40',
        );
    });

    it('names the load planner role when the server refuses the role', () => {
        const failure: ApiFailure = { ok: false, status: 403, message: 'You are not allowed to do this.' };

        expect(positionNotChangedText('AKE12345KL', failure)).toBe(
            'Position of AKE12345KL not changed: Only a load planner can change positions.',
        );
    });

    it('shows the server text for a conflict that is not a loading refusal', () => {
        const failure: ApiFailure = {
            ok: false,
            status: 409,
            message: 'Position RL on flight HJ221/2026-10-22/BRU is already taken by ULD PMC00001KL',
        };

        expect(positionNotChangedText('AKE12345KL', failure)).toBe(
            'Position of AKE12345KL not changed: Position RL on flight HJ221/2026-10-22/BRU is already taken by ULD PMC00001KL',
        );
    });

    it('shows the message when cargo did not answer', () => {
        const failure: ApiFailure = { ok: false, status: null, message: 'Cargo service is unreachable.' };

        expect(positionNotChangedText('AKE12345KL', failure)).toBe(
            'Position of AKE12345KL not changed: Cargo service is unreachable.',
        );
    });
});

describe('loadNotCheckedText', () => {
    it('writes the loading refusal in brussels time', () => {
        const failure: ApiFailure = {
            ok: false,
            status: 409,
            message: 'Loading on HJ221/2026-10-22/BRU is not allowed: the cargo plan is finalized',
            loadingRefusal: { refusal: 'PLAN_FINALIZED', opensAt: OPENS_AT, closesAt: CLOSES_AT },
        };

        expect(loadNotCheckedText(failure)).toBe('Load not checked: Closed: the cargo plan is finalized');
    });

    it('shows the message of any other failure', () => {
        const failure: ApiFailure = { ok: false, status: 503, message: 'Flight Operations is unavailable, try again later' };

        expect(loadNotCheckedText(failure)).toBe('Load not checked: Flight Operations is unavailable, try again later');
    });
});

describe('loadNotFinalizedText', () => {
    it('says a hard check failed when the refusal carries the report', () => {
        const failure: ApiFailure = {
            ok: false,
            status: 409,
            message: 'Load of flight HJ221/2026-10-22/BRU is not finalized: a hard check failed',
            loadCheckReport: { hardChecksPassed: false, results: [] },
        };

        expect(loadNotFinalizedText(failure)).toBe('Load not finalized: a hard check failed. Fix the load and finalize again.');
    });

    it('names the load planner role on a 403', () => {
        const failure: ApiFailure = { ok: false, status: 403, message: 'You are not allowed to do this.' };

        expect(loadNotFinalizedText(failure)).toBe('Load not finalized: Only a load planner can finalize the load.');
    });

    it('writes a loading refusal in brussels time', () => {
        const failure: ApiFailure = {
            ok: false,
            status: 409,
            message: 'Loading on HJ221/2026-10-22/BRU is not allowed: the loading window closed at 2026-10-22T07:40:00Z',
            loadingRefusal: { refusal: 'WINDOW_CLOSED', opensAt: OPENS_AT, closesAt: CLOSES_AT },
        };

        expect(loadNotFinalizedText(failure)).toBe('Load not finalized: Closed: the loading window closed at 22/10/2026 09:40');
    });
});
