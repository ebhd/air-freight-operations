# A second pair of eyes for flight data

Ebrahim Hdida. IAI

## The problem

A flight can pass our validation but still contain a mistake. example:, a route that usually takes two hours could be entered with an eight-hour duration. Both times are valid, but the difference deserves attention.

## The data we already have

Our `flight.created` event contains the route and scheduled times, which let us calculate the planned duration. Flights are kept for seven days, so the analysis would need its own longer history.

## From data to intelligence

The service would learn the usual duration range for each route from previous schedules. It would flag new flights far outside that range:

“This flight is scheduled to take eight hours. Previous flights on this route usually take around two hours.”

## Where it fits in the architecture

A small analysis service would consume `flight.created` through its own queue on `flight.events` and store its history in its own database.

## Limits and responsibility

Unusual does not always mean wrong. The service would warn the planner without changing times or blocking flights. Repeated mistakes could also affect its reference data. 

## Conclusion

Historical flight data can help planners notice mistakes that normal validation misses. 
