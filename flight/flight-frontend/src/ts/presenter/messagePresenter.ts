const errorBox = document.querySelector<HTMLElement>('#error')!;

export function showError(error: unknown): void {
    errorBox.textContent = error instanceof Error ? error.message : 'Something went wrong. Please try again.';
    errorBox.hidden = false;
}

export function clearError(): void {
    errorBox.textContent = '';
    errorBox.hidden = true;
}
