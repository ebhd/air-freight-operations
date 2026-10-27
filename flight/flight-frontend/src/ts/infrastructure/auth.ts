import Keycloak from 'keycloak-js';

const keycloak = new Keycloak({
    url: import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:8180',
    realm: import.meta.env.VITE_KEYCLOAK_REALM ?? 'air-freight',
    clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'flight-frontend',
});

const loggedIn = keycloak.init({
    onLoad: 'login-required',
    pkceMethod: 'S256',
    checkLoginIframe: false,
});

export async function userName(): Promise<string> {
    await loggedIn;
    return keycloak.tokenParsed?.preferred_username ?? '';
}

export function hasRole(role: string): boolean {
    return keycloak.hasRealmRole(role);
}

export async function logout(): Promise<void> {
    await keycloak.logout({
        redirectUri: window.location.origin + window.location.pathname + window.location.search,
    });
}

export async function authorizedFetch(url: string, init: RequestInit = {}): Promise<Response> {
    await loggedIn;
    try {
        await keycloak.updateToken(30);
    } catch {
        throw new Error('Your sign-in could not be renewed. Reload the page to sign in again.');
    }
    const headers = new Headers(init.headers);
    headers.set('Authorization', `Bearer ${keycloak.token}`);
    return fetch(url, { ...init, headers });
}
