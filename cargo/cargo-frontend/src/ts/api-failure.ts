import type { LoadCheckReport } from './load-check.js';

// the codes of the java enum LoadingRefusal
export type LoadingRefusal = 'PLAN_FINALIZED' | 'FLIGHT_NOT_PLANNED' | 'WINDOW_NOT_OPEN_YET' | 'WINDOW_CLOSED';

// cargo adds the refusal and both window times to a 409 so the page can write its own text
export type LoadingRefusalDetail = {
    refusal: LoadingRefusal;
    opensAt: string;
    closesAt: string;
};

// a failure carries the message to show and the http status which is null when the server did not answer
export type ApiFailure = {
    ok: false;
    status: number | null;
    message: string;
    loadingRefusal?: LoadingRefusalDetail;
    // only when finalizing was refused because a hard check failed
    loadCheckReport?: LoadCheckReport;
};

// the problem json body that spring sends for a refused request
export type ProblemDetail = {
    detail?: string;
    refusal?: LoadingRefusal | 'HARD_CHECKS_FAILED';
    report?: LoadCheckReport;
    opensAt?: string;
    closesAt?: string;
};

// null when the answer had no problem json body
export function failureFromProblem(status: number, problem: ProblemDetail | null): ApiFailure {
    const failure: ApiFailure = { ok: false, status, message: `Request failed (HTTP ${status})` };
    if (problem === null) {
        return failure;
    }
    if (problem.detail) {
        failure.message = problem.detail;
    }
    // all three or none so the page never writes half a refusal
    if (problem.refusal && problem.refusal !== 'HARD_CHECKS_FAILED' && problem.opensAt && problem.closesAt) {
        failure.loadingRefusal = { refusal: problem.refusal, opensAt: problem.opensAt, closesAt: problem.closesAt };
    }
    // the same report as the load check so the page can show what to fix
    if (problem.refusal === 'HARD_CHECKS_FAILED' && problem.report) {
        failure.loadCheckReport = problem.report;
    }
    return failure;
}
