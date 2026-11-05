import { describe, expect, it } from 'vitest';
import type { Flight } from '../../ts/domain/Flight';
import {
    flightLabel,
    hoursAndMinutes,
    replacementKey,
    replannedText,
    replanningOffer,
} from '../../ts/presenter/replanning';

// the plan example: HJ500 with 740 minutes becomes HJ500A departing 11 nov 05:00Z (06:00 brussels)
const PROBLEM = {
    detail: 'All delays together would be 740 minutes (12 h 20 min). ...',
    refusal: 'TOTAL_DELAY_EXCEEDED',
    totalMinutes: 740,
    replanningPossible: true,
    newFlightNumber: 'HJ500A',
    newScheduledDeparture: '2026-11-11T05:00:00Z',
    newScheduledArrival: '2026-11-11T10:20:00Z',
};

describe('replanningOffer', () => {
    it('offers the proposed flight to a flight planner', () => {
        expect(replanningOffer(PROBLEM, true)).toEqual({
            totalMinutes: 740,
            newFlightNumber: 'HJ500A',
            newScheduledDeparture: '2026-11-11T05:00:00Z',
            newScheduledArrival: '2026-11-11T10:20:00Z',
        });
    });

    it.each([
        ['another role', PROBLEM, false],
        ['another refusal', { ...PROBLEM, refusal: 'NOT_PLANNED' }, true],
        ['a departed flight', { ...PROBLEM, replanningPossible: false }, true],
        ['a refusal without the proposal', { ...PROBLEM, newFlightNumber: undefined }, true],
        ['a plain error', { detail: 'delay minutes must be between 1 and 720' }, true],
    ])('offers nothing for %s', (_case, problem, planner) => {
        expect(replanningOffer(problem, planner)).toBeNull();
    });
});

describe('hoursAndMinutes', () => {
    it.each([
        [720, '12 h'],
        [721, '12 h 1 min'],
        [740, '12 h 20 min'],
    ])('%s minutes reads as %s', (minutes, text) => {
        expect(hoursAndMinutes(minutes)).toBe(text);
    });
});

describe('replannedText', () => {
    it('says where the flight went and whether the new flight exists', () => {
        const waiting = replanned(false);
        expect(replannedText(waiting)).toBe(
            'This flight was replanned to HJ500A departing 11/11/2026 06:00 Brussels time. ' +
                'Waiting for the planning system to create the new flight.',
        );
        expect(replacementKey(waiting)).toBeNull();
        const created = replanned(true);
        expect(replannedText(created)).toBe('This flight was replanned to HJ500A departing 11/11/2026 06:00 Brussels time.');
        expect(replacementKey(created)).toEqual({
            flightNumber: 'HJ500A',
            scheduledDepartureDate: '2026-11-11',
            departureAirportCode: 'BRU',
        });
    });

    it('is empty for a normal flight', () => {
        expect(replannedText({ ...replanned(false), status: 'PLANNED', replanning: null })).toBe('');
        expect(flightLabel({ flightNumber: 'HJ500', scheduledDepartureDate: '2026-11-10', departureAirportCode: 'BRU' })).toBe(
            'HJ500 of 2026-11-10',
        );
    });
});

function replanned(replacementCreated: boolean): Flight {
    return {
        flightNumber: 'HJ500',
        scheduledDepartureDate: '2026-11-10',
        departureAirportCode: 'BRU',
        arrivalAirportCode: 'AMS',
        status: 'REPLANNED',
        scheduledDeparture: '2026-11-10T20:00:00Z',
        scheduledArrival: '2026-11-10T22:00:00Z',
        estimatedDeparture: '2026-11-11T02:40:00Z',
        estimatedArrival: '2026-11-11T08:00:00Z',
        actualDeparture: null,
        actualArrival: null,
        departureDelays: [],
        arrivalDelays: [],
        replaces: null,
        replanning: {
            newFlightNumber: 'HJ500A',
            newScheduledDepartureDate: '2026-11-11',
            newScheduledDeparture: '2026-11-11T05:00:00Z',
            newScheduledArrival: '2026-11-11T10:20:00Z',
            replacementCreated,
            replannedAt: '2026-11-10T14:03:12Z',
            delayType: 'DEPARTURE',
            minutes: 140,
            reasonCode: '93',
            description: 'Hydraulic leak',
        },
    };
}
