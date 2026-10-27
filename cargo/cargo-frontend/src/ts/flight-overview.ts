// one flight as sent by FlightOverviewResponse in the flight list and in the cargo plan
export type FlightOverview = {
    flightNumber: string;
    scheduledDepartureDate: string;
    departureAirportCode: string;
    arrivalAirportCode: string;
    status: string;
    scheduledDeparture: string;
    scheduledArrival: string;
};
