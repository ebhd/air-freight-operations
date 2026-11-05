# From flight data to delay predictions

Fabio Di Nota. Topic (IAI): Data → predictions and/or intelligence.

## The problem

Air Freight Operations only knows about a delay once a flight planner enters it. Until then, ETD and ETA simply repeat the schedule, so load planners work against a loading window that may still move and partners only hear about new times afterwards. An early warning would give them time to act.

## The data we already have

We keep flights for 7 days, including delays and their reasons, and the scheduled, estimated and actual times. That is too little to learn from, so the model would have to build its own history from our flight events.

## From data to prediction

The model would give each flight a delay risk score: 70% means a 70% chance that the flight leaves late, so load planners and partners can be warned earlier. It only looks at what is known before departure: route, time of day, weekday and whether the aircraft's previous flight was late, which is probably the strongest signal. We would check it by comparing its scores with the actual times.

## Where it fits in the architecture

The prediction model would be its own bounded context, downstream of Flight Operations. It binds its own queue to the `flight.events` exchange, stores the events in a separate database and learns from them. Flight only needs one new event on that exchange, `flight.delay-recorded`, because the delay reason isn't in our events yet. Only the prediction service listens to it, so the messages to partners stay the same.

## Limits and responsibility

We can't tell other operations to “take their time, the flight will most likely get delayed anyway”: if it doesn't, they won't be done before the STD. So this model won't act as a new schedule but as an early warning, and everyone keeps working towards the STD. The score never changes the ETD or the loading window; only a delay registered by a flight planner does. Our data is also simulated and has no weather information, so a real model would need real history and outside data.

## Conclusion

Every system holds valuable data. It's just a matter of looking at it with fresh eyes and finding what gives the most value for the time you spend building it. A delay prediction lets all operations prepare before the delay arrives, which means smarter planning and better service.
