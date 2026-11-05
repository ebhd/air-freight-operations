import type { Flight, FlightKey } from '../domain/Flight';
import type { ProblemDetail } from '../infrastructure/restUtils';
import type { ConfirmStep } from './confirmDialog';
import { formatBrusselsDateTime } from './timeFormat';

// what the replan question needs from a 409 TOTAL_DELAY_EXCEEDED
export type ReplanningOffer = {
    totalMinutes: number;
    newFlightNumber: string;
    newScheduledDeparture: string;
    newScheduledArrival: string;
};

// null unless the refusal says replanning is possible and this user may do it
export function replanningOffer(problem: ProblemDetail, isFlightPlanner: boolean): ReplanningOffer | null {
    if (
        !isFlightPlanner ||
        problem.refusal !== 'TOTAL_DELAY_EXCEEDED' ||
        !problem.replanningPossible ||
        problem.totalMinutes === undefined ||
        !problem.newFlightNumber ||
        !problem.newScheduledDeparture ||
        !problem.newScheduledArrival
    ) {
        return null;
    }
    return {
        totalMinutes: problem.totalMinutes,
        newFlightNumber: problem.newFlightNumber,
        newScheduledDeparture: problem.newScheduledDeparture,
        newScheduledArrival: problem.newScheduledArrival,
    };
}

// 740 becomes 12 h 20 min and 720 becomes 12 h
export function hoursAndMinutes(minutes: number): string {
    const hours = Math.floor(minutes / 60);
    const rest = minutes % 60;
    return rest === 0 ? `${hours} h` : `${hours} h ${rest} min`;
}

// the question the planner answers before the flight is replanned, times in brussels time
// why the replan dialog appears
export function replanReason(offer: ReplanningOffer): string {
    return `All delays together would be ${hoursAndMinutes(offer.totalMinutes)}, more than 12 h.`;
}

function replanTimes(departure: string, arrival: string): ConfirmStep['details'] {
    return [
        { label: 'Departure (Brussels)', value: formatBrusselsDateTime(departure) },
        { label: 'Arrival (Brussels)', value: formatBrusselsDateTime(arrival) },
    ];
}

// the current flight ends and the new one takes over, shown as from and to in the dialog
export function replanSteps(flight: Flight, offer: ReplanningOffer): ConfirmStep[] {
    return [
        { label: 'From', title: flight.flightNumber, details: replanTimes(flight.scheduledDeparture, flight.scheduledArrival) },
        { label: 'To', title: offer.newFlightNumber, details: replanTimes(offer.newScheduledDeparture, offer.newScheduledArrival) },
    ];
}

// the status line of a replanned flight
export function replannedText(flight: Flight): string {
    const replanning = flight.replanning;
    if (flight.status !== 'REPLANNED' || replanning === null) {
        return '';
    }
    const when = `${formatBrusselsDateTime(replanning.newScheduledDeparture)} Brussels time`;
    const to = `This flight was replanned to ${replanning.newFlightNumber} departing ${when}.`;
    return replanning.replacementCreated ? to : `${to} Waiting for the planning system to create the new flight.`;
}

// the key of the new flight, known once the planning system created it
export function replacementKey(flight: Flight): FlightKey | null {
    if (flight.replanning === null || !flight.replanning.replacementCreated) {
        return null;
    }
    return {
        flightNumber: flight.replanning.newFlightNumber,
        scheduledDepartureDate: flight.replanning.newScheduledDepartureDate,
        departureAirportCode: flight.departureAirportCode,
    };
}

export function flightLabel(key: FlightKey): string {
    return `${key.flightNumber} of ${key.scheduledDepartureDate}`;
}
