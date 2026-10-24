import { showError } from './api.js';
import { logout, userName } from './auth.js';
import { requiredElement } from './dom.js';

export async function initializePage(load: () => Promise<void>): Promise<void> {
    try {
        requiredElement('#user-name', HTMLElement).textContent = await userName();
    } catch {
        showError('Sign-in could not be completed. Check Keycloak and reload the page.');
        return;
    }
    const signOutButton = requiredElement('#sign-out', HTMLButtonElement);
    signOutButton.hidden = false;
    signOutButton.addEventListener('click', async () => {
        try {
            await logout();
        } catch {
            showError('Sign-out could not be completed. Please try again.');
        }
    });
    requiredElement('#content', HTMLElement).hidden = false;
    await load();
}
