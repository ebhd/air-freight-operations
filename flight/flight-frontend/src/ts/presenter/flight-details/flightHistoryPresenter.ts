import type { Flight, FlightKey } from '../../domain/Flight';
import { flightDetailsPage, getFlight } from '../../infrastructure/flightRest';
import { flightLabel, replacementKey } from './replanning';

const historySection = document.querySelector<HTMLElement>('#history')!;
const historyLines = document.querySelector<HTMLUListElement>('#history-lines')!;
let historyRun = 0;

export function clearHistory(): void {
    historyRun += 1;
    historyLines.replaceChildren();
    historySection.hidden = true;
}

export async function displayHistory(flight: Flight): Promise<void> {
    clearHistory();
    const run = historyRun;
    const lines: HTMLLIElement[] = [];
    let earlier = flight.replaces;
    while (earlier !== null) {
        lines.unshift(historyLine('Replaces', earlier));
        earlier = (await flightOrNull(earlier))?.replaces ?? null;
    }
    let later = replacementKey(flight);
    while (later !== null) {
        lines.push(historyLine('Replanned to', later));
        const next = await flightOrNull(later);
        later = next === null ? null : replacementKey(next);
    }
    if (run !== historyRun) {
        // a newer display started or the page moved on while the chain was loading
        return;
    }
    historyLines.append(...lines);
    historySection.hidden = lines.length === 0;
}

async function flightOrNull(key: FlightKey): Promise<Flight | null> {
    try {
        return await getFlight(key);
    } catch {
        return null;
    }
}

function historyLine(text: string, key: FlightKey): HTMLLIElement {
    const link = document.createElement('a');
    link.href = flightDetailsPage(key);
    link.className = 'font-medium text-blue-800 underline hover:text-blue-600';
    link.textContent = flightLabel(key);
    const line = document.createElement('li');
    line.append(`${text} `, link);
    return line;
}
