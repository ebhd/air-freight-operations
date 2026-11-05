import { describe, expect, it } from 'vitest';
import { movedToLink, movedToText } from '../../ts/replanning.js';

const MOVED_TO = { flightNumber: 'HJ500A', scheduledDepartureDate: '2026-11-11', departureAirportCode: 'BRU' };

describe('moved to banner', () => {
    it('names the new flight', () => {
        expect(movedToText(MOVED_TO)).toBe('This flight was replanned. Its cargo moved to flight HJ500A of 2026-11-11:');
    });

    it('links to the cargo plan of the new flight', () => {
        expect(movedToLink(MOVED_TO)).toBe(
            'cargo-plan.html?flightNumber=HJ500A&scheduledDepartureDate=2026-11-11&departureAirportCode=BRU',
        );
    });
});
