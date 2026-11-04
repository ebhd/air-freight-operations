import { logout, userName } from '../infrastructure/auth';
import { showError } from './messagePresenter';

export async function initializeUser(): Promise<void> {
    document.querySelector<HTMLElement>('#user-name')!.textContent = await userName();
    const signOutButton = document.querySelector<HTMLButtonElement>('#sign-out')!;
    signOutButton.hidden = false;
    signOutButton.addEventListener('click', async () => {
        try {
            await logout();
        } catch {
            showError(new Error('Sign-out could not be completed. Please try again.'));
        }
    });
}
