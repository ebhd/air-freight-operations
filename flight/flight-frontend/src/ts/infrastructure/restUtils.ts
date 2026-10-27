import { authorizedFetch } from './auth';

// keeps the http status so a page can react to a refusal
export class ResponseError extends Error {
    constructor(message: string, readonly status: number) {
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
        const problem = await response.json() as { detail?: string };
        if (problem.detail) {
            throw new ResponseError(problem.detail, response.status);
        }
    }
    throw new ResponseError(`Request failed (HTTP ${response.status}).`, response.status);
}
