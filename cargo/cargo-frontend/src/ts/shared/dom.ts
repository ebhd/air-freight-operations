// stops the page with a clear message when the html and the script disagree
export function requiredElement<T extends Element>(selector: string, type: new () => T): T {
    const element = document.querySelector(selector);
    if (!(element instanceof type)) {
        throw new Error(`Page element ${selector} is missing or is not a ${type.name}`);
    }
    return element;
}

// append adds text as plain text and never as html
export function cell(content: string | HTMLElement): HTMLTableCellElement {
    const td = document.createElement('td');
    td.append(content);
    return td;
}
