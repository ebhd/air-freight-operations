export type ConfirmOptions = {
    title: string;
    message: string;
    confirmLabel: string;
    danger?: boolean;
};

const BUTTON = 'cursor-pointer rounded px-3 py-2 font-medium';

function button(text: string, value: string, classes: string): HTMLButtonElement {
    const element = document.createElement('button');
    element.textContent = text;
    element.value = value;
    element.className = `${BUTTON} ${classes}`;
    return element;
}

// a native dialog: the browser keeps the focus inside and closes it on escape
export function confirmDialog(options: ConfirmOptions): Promise<boolean> {
    const dialog = document.createElement('dialog');
    dialog.className = 'm-auto w-[calc(100%-2rem)] max-w-md rounded border border-slate-200 bg-white text-slate-800 shadow-lg backdrop:bg-slate-900/50';

    const form = document.createElement('form');
    form.method = 'dialog';
    form.className = 'p-6';

    const title = document.createElement('h2');
    title.textContent = options.title;
    title.className = 'mb-2 text-xl font-semibold';

    const message = document.createElement('p');
    message.textContent = options.message;
    message.className = 'mb-6';

    const cancel = button('Cancel', 'cancel', 'border border-slate-300 bg-white hover:bg-slate-100');
    // the safe answer has the focus, so pressing enter never confirms by accident
    cancel.autofocus = true;
    const confirm = button(
        options.confirmLabel,
        'confirm',
        options.danger ? 'bg-red-700 text-white hover:bg-red-800' : 'bg-blue-900 text-white hover:bg-blue-800',
    );

    const buttons = document.createElement('div');
    buttons.className = 'flex flex-wrap justify-end gap-2';
    buttons.append(cancel, confirm);
    form.append(title, message, buttons);
    dialog.append(form);

    // a click on the backdrop counts as cancel, the form covers the whole dialog itself
    dialog.addEventListener('click', event => {
        if (event.target === dialog) {
            dialog.close();
        }
    });

    return new Promise(resolve => {
        dialog.addEventListener('close', () => {
            resolve(dialog.returnValue === 'confirm');
            dialog.remove();
        });
        document.body.append(dialog);
        dialog.showModal();
    });
}
