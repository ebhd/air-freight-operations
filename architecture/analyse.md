# Air Freight Operations

An airline carries cargo between airports on a fixed flight schedule. Flight planners record delays, their causes, and actual departure and arrival times. Load planners assign booked containers and pallets (ULDs) to aircraft positions, check the load, and finalize it.

Flight schedules and cargo bookings arrive from existing data feeds. The system keeps flight and load information current and creates standard IATA messages for other parties at the appropriate moments, even before any recipients are connected.

## 1. Subdomains

| Subdomain | Type | Why |
| --- | --- | --- |
| Flight Planning | Supporting | It keeps flight times and delays reliable so operations can respond. |
| Freight Loading | Core | Planning, checking and finalising the cargo load is central to this air-freight system. |

## 2. Bounded contexts

Describe the bounded contexts and their responsibilities. Add a context diagram when they are known.

## 3. Ubiquitous language

| Term | Meaning by context |
|------|--------------------|
| To define | To define |

## 4. Context map

Describe how the contexts relate, which is upstream or downstream, and how they communicate.

| Pattern | Where and why |
|---------|---------------|
| To define | To define |

## 5. Deployment

Describe the deployable services, their data stores, and communication paths. Add a deployment diagram when known.

## 6. Tactical design: first bounded context

Replace this heading with the context name. Add a class diagram and identify the tactical building blocks.

| Building block | Elements |
|----------------|----------|
| Aggregate root | To define |
| Entity | To define |
| Value object | To define |
| Domain event | To define |
| Repository | To define |

### Invariants, lifecycle and events

Document the rules that must always hold, the state transitions, and emitted events.

## 7. Tactical design: second bounded context

Replace this heading with the context name. Add its class diagram, building blocks, rules, lifecycle and events. Add or remove context sections as needed.

## 8. Layering

Describe the layers, dependency direction, ports, adapters, and how the architecture is checked.

## 9. Running it

Add build, test and run commands when available. Record sample requests and error responses here.
