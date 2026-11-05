// the key of the new flight as sent by CargoPlanResponse.movedTo
export type MovedTo = {
    flightNumber: string;
    scheduledDepartureDate: string;
    departureAirportCode: string;
};

// the banner on the cargo plan of a replanned flight
export function movedToText(movedTo: MovedTo): string {
    return `This flight was replanned. Its cargo moved to flight ${movedTo.flightNumber} of ${movedTo.scheduledDepartureDate}:`;
}

// the link to the cargo plan of the new flight, encoded like every cargo plan link
export function movedToLink(movedTo: MovedTo): string {
    return `cargo-plan.html?${new URLSearchParams(movedTo)}`;
}
