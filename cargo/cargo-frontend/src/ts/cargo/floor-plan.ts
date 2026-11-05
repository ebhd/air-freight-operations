import mainDeck from '../../images/main-deck.svg?raw';

// inline because a script cannot reach inside an img, the svg is our own file and never server text
export function renderFloorPlan(element: HTMLElement, taken: Map<string, string>): void {
    element.innerHTML = mainDeck;
    element.querySelectorAll<SVGGElement>('[data-position]').forEach((position) => {
        const code = position.dataset.position ?? '';
        const uldCode = taken.get(code);
        if (uldCode !== undefined) {
            position.classList.add('taken');
            position.append(tooltip(`${code}: ${uldCode}`));
        }
    });
}

// the browser shows the title of an svg element on hover
function tooltip(text: string): SVGTitleElement {
    const title = document.createElementNS('http://www.w3.org/2000/svg', 'title');
    title.textContent = text;
    return title;
}
