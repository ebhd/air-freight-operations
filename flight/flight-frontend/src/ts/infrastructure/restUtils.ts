import { authorizedFetch } from './auth';

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
        throw new Error('Your sign-in was not accepted. Reload the page to sign in again.');
    }
    if (response.status === 403) {
        throw new Error('You are not allowed to do this.');
    }
    if (response.headers.get('Content-Type')?.includes('application/problem+json')) {
        const problem = await response.json() as { detail?: string };
        if (problem.detail) {
            throw new Error(problem.detail);
        }
    }
    throw new Error(`Request failed (HTTP ${response.status}).`);
}
