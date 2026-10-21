import { getAccessToken, signOut } from './auth.js';
import { requiredElement } from './dom.js';

// a failure carries the message to show so a page never reads http status codes
export type ApiResult<T> = { ok: true; data: T } | { ok: false; message: string };

type ProblemDetail = {
    detail?: string;
};

export async function getJson<T>(path: string): Promise<ApiResult<T>> {
    let response: Response;
    try {
        response = await fetch(path, {
            headers: { Authorization: `Bearer ${getAccessToken()}` },
        });
    } catch {
        return { ok: false, message: 'Cargo service is unreachable.' };
    }
    if (response.ok) {
        // typescript does not check json at runtime so the requested type must match the java response
        return { ok: true, data: (await response.json()) as T };
    }
    if (response.status === 401) {
        signOut();
        return { ok: false, message: 'Your session has expired. Please sign in again.' };
    }
    if (response.status === 403) {
        return { ok: false, message: 'You are not allowed to do this.' };
    }
    return { ok: false, message: await problemDetail(response) };
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
