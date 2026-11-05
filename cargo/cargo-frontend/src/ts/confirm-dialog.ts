export type ConfirmDetail = {
    label: string;
    value: string;
};

export type ConfirmOptions = {
    title: string;
    message: string;
    confirmLabel: string;
    danger?: boolean;
    details?: ConfirmDetail[];
};

const BUTTON = 'h-10 cursor-pointer rounded-md px-4 text-sm font-medium';

function button(text: string, value: string, classes: string): HTMLButtonElement {
    const element = document.createElement('button');
    element.textContent = text;
    element.value = value;
    element.className = `${BUTTON} ${classes}`;
    return element;
}

function detailRows(details: ConfirmDetail[], className: string): HTMLDListElement {
    const list = document.createElement('dl');
    list.className = className;
    for (const detail of details) {
        const row = document.createElement('div');
        row.className = 'flex justify-between gap-4 py-1.5';
        const label = document.createElement('dt');
        label.textContent = detail.label;
        label.className = 'shrink-0 text-slate-500';
        const value = document.createElement('dd');
        value.textContent = detail.value;
        value.className = 'min-w-0 text-right font-medium break-words';
        row.append(label, value);
        list.append(row);
    }
    return list;
}

// a native dialog: the browser keeps the focus inside and closes it on escape
export function confirmDialog(options: ConfirmOptions): Promise<boolean> {
    const dialog = document.createElement('dialog');
    dialog.className = 'm-auto w-[calc(100%-2rem)] max-w-md overflow-hidden rounded-lg border border-slate-200 bg-white text-slate-800 shadow-xl backdrop:bg-slate-900/50';

    const form = document.createElement('form');
    form.method = 'dialog';

    const title = document.createElement('h2');
    title.textContent = options.title;
    title.className = 'text-xl font-semibold text-blue-900';

    const message = document.createElement('p');
    message.textContent = options.message;
    message.className = 'mt-1 text-sm text-slate-600';

    const content = document.createElement('div');
    content.className = 'p-6';
    content.append(title, message);
    if (options.details) {
        content.append(detailRows(options.details, 'mt-4 divide-y divide-slate-200 rounded-md border border-slate-200 bg-slate-50 px-3 text-sm'));
    }

    const cancel = button('Cancel', 'cancel', 'border border-slate-300 bg-white hover:bg-slate-100');
    // the safe answer has the focus, so pressing enter never confirms by accident
    cancel.autofocus = true;
    const confirm = button(
        options.confirmLabel,
        'confirm',
        options.danger ? 'bg-red-700 text-white hover:bg-red-800' : 'bg-blue-900 text-white hover:bg-blue-800',
    );

    const buttons = document.createElement('div');
    buttons.className = 'flex flex-wrap justify-end gap-2 border-t border-slate-200 bg-slate-50 px-6 py-4';
    buttons.append(cancel, confirm);
    form.append(content, buttons);
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
