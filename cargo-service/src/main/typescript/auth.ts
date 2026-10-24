import { requiredElement } from './dom.js';

// development sign-in with the keycloak password grant until #1 replaces it
const TOKEN_URL = 'http://localhost:8180/realms/air-freight/protocol/openid-connect/token';
const CLIENT_ID = 'air-freight-dev';

const TOKEN_KEY = 'accessToken';

// keycloak sends more fields but the page only needs the token
type TokenResponse = {
    access_token: string;
};

type SignInResult = { ok: true } | { ok: false; message: string };

export function getAccessToken(): string | null {
    return sessionStorage.getItem(TOKEN_KEY);
}

async function signIn(username: string, password: string): Promise<SignInResult> {
    let response: Response;
    try {
        response = await fetch(TOKEN_URL, {
            method: 'POST',
            body: new URLSearchParams({
                client_id: CLIENT_ID,
                grant_type: 'password',
                username,
                password,
            }),
        });
    } catch {
        return { ok: false, message: 'Keycloak is unreachable.' };
    }
    if (response.status === 401) {
        return { ok: false, message: 'Wrong username or password.' };
    }
    if (!response.ok) {
        return { ok: false, message: `Sign-in failed (HTTP ${response.status})` };
    }
    const token = (await response.json()) as TokenResponse;
    sessionStorage.setItem(TOKEN_KEY, token.access_token);
    return { ok: true };
}

export function signOut(): void {
    sessionStorage.removeItem(TOKEN_KEY);
    showSignedIn(false);
}

export function whenSignedIn(onSignedIn: () => void): void {
    const form = requiredElement('#sign-in', HTMLFormElement);
    const username = requiredElement('#sign-in [name="username"]', HTMLInputElement);
    const password = requiredElement('#sign-in [name="password"]', HTMLInputElement);
    const formError = requiredElement('#sign-in-error', HTMLElement);

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        formError.hidden = true;
        const result = await signIn(username.value, password.value);
        if (!result.ok) {
            formError.textContent = result.message;
            formError.hidden = false;
            return;
        }
        form.reset();
        showSignedIn(true);
        onSignedIn();
    });
    requiredElement('#sign-out', HTMLButtonElement).addEventListener('click', signOut);

    if (getAccessToken()) {
        showSignedIn(true);
        onSignedIn();
    } else {
        showSignedIn(false);
    }
}

// the form and the page content are never visible together
function showSignedIn(signedIn: boolean): void {
    requiredElement('#sign-in', HTMLFormElement).hidden = signedIn;
    requiredElement('#content', HTMLElement).hidden = !signedIn;
    requiredElement('#sign-out', HTMLButtonElement).hidden = !signedIn;
}
