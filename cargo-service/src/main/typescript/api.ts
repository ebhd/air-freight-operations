import { AuthenticationError, authorizedFetch } from './auth.js';
import { requiredElement } from './dom.js';

// a failure carries the message to show and the http status which is null when the server did not answer
export type ApiResult<T> = { ok: true; data: T } | { ok: false; status: number | null; message: string };

type ProblemDetail = {
    detail?: string;
};

export async function getJson<T>(path: string): Promise<ApiResult<T>> {
    const result = await request(path, {});
    if (!result.ok) {
        return result;
    }
    // typescript does not check json at runtime so the requested type must match the java response
    return { ok: true, data: (await result.data.json()) as T };
}

// a 204 answer has no body so success carries null
export async function sendJson(method: 'PUT' | 'DELETE', path: string, body?: object): Promise<ApiResult<null>> {
    const init: RequestInit = { method };
    if (body !== undefined) {
        init.headers = { 'Content-Type': 'application/json' };
        init.body = JSON.stringify(body);
    }
    const result = await request(path, init);
    if (!result.ok) {
        return result;
    }
    return { ok: true, data: null };
}

// adds the token and turns every failed request into a failure with the message to show
async function request(path: string, init: RequestInit): Promise<ApiResult<Response>> {
    let response: Response;
    try {
        response = await authorizedFetch(path, init);
    } catch (error) {
        if (error instanceof AuthenticationError) {
            return { ok: false, status: null, message: error.message };
        }
        return { ok: false, status: null, message: 'Cargo service is unreachable.' };
    }
    if (response.ok) {
        return { ok: true, data: response };
    }
    if (response.status === 401) {
        return { ok: false, status: response.status, message: 'Your sign-in was not accepted. Reload the page to sign in again.' };
    }
    if (response.status === 403) {
        return { ok: false, status: response.status, message: 'You are not allowed to do this.' };
    }
    return { ok: false, status: response.status, message: await problemDetail(response) };
}

// spring explains a refused request in the detail of a problem json body
async function problemDetail(response: Response): Promise<string> {
    const contentType = response.headers.get('Content-Type') ?? '';
    if (contentType.includes('application/problem+json')) {
        const problem = (await response.json()) as ProblemDetail;
        if (problem.detail) {
            return problem.detail;
        }
    }
    return `Request failed (HTTP ${response.status})`;
}

// textContent never runs server text as html
export function showError(message: string): void {
    const box = requiredElement('#error', HTMLElement);
    box.textContent = message;
    box.hidden = false;
}

export function clearError(): void {
    const box = requiredElement('#error', HTMLElement);
    box.textContent = '';
    box.hidden = true;
}
