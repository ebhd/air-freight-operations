import { authorizedFetch } from './auth';

// the problem json body of a refusal, latestAllowed only comes with TOO_LATE and the replanning
// fields only with TOTAL_DELAY_EXCEEDED
export type ProblemDetail = {
    detail?: string;
    refusal?: string;
    latestAllowed?: string;
    totalMinutes?: number;
    replanningPossible?: boolean;
    newFlightNumber?: string;
    newScheduledDeparture?: string;
    newScheduledArrival?: string;
};

// keeps the http status and the problem body so a page can react to a refusal
export class ResponseError extends Error {
    constructor(message: string, readonly status: number, readonly problem: ProblemDetail = {}) {
        super(message);
    }
}

export async function request(url: string, init: RequestInit = {}): Promise<Response> {
    let response: Response;
    try {
        response = await authorizedFetch(url, init);
    } catch (error) {
        if (error instanceof TypeError) {
            throw new Error('Flight service is unreachable.');
        }
        throw error;
    }
    if (!response.ok) {
        return throwResponseError(response);
    }
    return response;
}

async function throwResponseError(response: Response): Promise<never> {
    if (response.status === 401) {
        throw new ResponseError('Your sign-in was not accepted. Reload the page to sign in again.', response.status);
    }
    if (response.status === 403) {
        throw new ResponseError('You are not allowed to do this.', response.status);
    }
    if (response.headers.get('Content-Type')?.includes('application/problem+json')) {
        const problem = await response.json() as ProblemDetail;
        if (problem.detail) {
            throw new ResponseError(problem.detail, response.status, problem);
        }
    }
    throw new ResponseError(`Request failed (HTTP ${response.status}).`, response.status);
}
