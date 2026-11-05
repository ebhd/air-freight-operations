// no api import so vitest can load this module without keycloak
// one check of the report as sent by LoadCheckReportResponse
export type LoadCheckResult = {
    name: string;
    severity: 'HARD' | 'SOFT';
    passed: boolean;
    reason: string;
};

export type LoadCheckReport = {
    // false when a hard check failed so the load cannot be finalized
    hardChecksPassed: boolean;
    results: LoadCheckResult[];
};

export function severityText(result: LoadCheckResult): string {
    return result.severity === 'HARD' ? 'Hard' : 'Soft';
}

// a failed soft check only warns
export function outcomeText(result: LoadCheckResult): string {
    if (result.passed) {
        return 'Passed';
    }
    return result.severity === 'HARD' ? 'Failed' : 'Warning';
}

// one row per check with the reason as plain text
export function renderLoadCheck(rows: HTMLTableSectionElement, report: LoadCheckReport): void {
    rows.replaceChildren(
        ...report.results.map((result) => {
            const row = document.createElement('tr');
            row.className = '*:border-b *:border-slate-300 *:px-3 *:py-2';
            if (!result.passed) {
                row.classList.add(result.severity === 'HARD' ? 'bg-red-50' : 'bg-amber-50');
            }
            row.append(
                cell(result.name),
                cell(severityText(result)),
                cell(outcomeText(result)),
                cell(result.reason),
            );
            return row;
        }),
    );
}

// append adds text as plain text and never as html
function cell(text: string): HTMLTableCellElement {
    const td = document.createElement('td');
    td.append(text);
    return td;
}
