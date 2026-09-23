# Air Freight Operations

An airline carries cargo between airports on a fixed flight schedule. Flight planners record delays, their causes, and actual departure and arrival times. Load planners assign booked containers and pallets (ULDs) to aircraft positions, check the load, and finalize it.

Flight schedules and cargo bookings arrive from existing data feeds. The system keeps flight and load information current and creates standard IATA messages for other parties at the appropriate moments, even before any recipients are connected.

## 1. Subdomains

| Subdomain | Type | Why |
| --- | --- | --- |
| Flight Planning | Supporting | It keeps flight times and delays reliable so operations can respond. |
| Freight Loading | Core | Planning, checking and finalising the cargo load is central to this air-freight system. |
| Partner Messaging | Supporting | It creates the required flight and load messages for external parties when relevant events occur. |
| Identity and Access Management | Generic | Signing users in is a common problem handled by Keycloak; each operational context still enforces its own action rules. |

## 2. Bounded contexts

Proposed boundaries; the project case specifies the behavior, but leaves context and service ownership to the team.

```mermaid
flowchart LR
    subgraph FO["Flight Operations"]
        F["Flight, Delay<br/>STD/STA, ETD/ETA, ATD/ATA"]
    end
    subgraph CL["Cargo Loading"]
        C["ULD booking, Load<br/>Position, Floor Plan, Load Check"]
    end
    subgraph PC["Partner Communication"]
        M["FLM, MVT, CPM<br/>message identity and version"]
    end
    IAM["Identity and Access Management<br/>(Keycloak, external)"]

    F -->|flight identity, state and times| C
    F -->|flight changes| M
    C -->|finalized load| M
    M -->|processed CPM required for departure| F
    IAM -.->|identity and roles| F
    IAM -.->|identity and roles| C
```

Flight Operations and Cargo Loading enforce their own action rules. The diagram shows logical boundaries; communication and deployment still need to be designed.

## 3. Ubiquitous language

The same term can have a different role in each context. A dash means the term is not part of that context's model.

| Term | Flight Operations | Cargo Loading | Partner Communication | IAM (Keycloak) |
| --- | --- | --- | --- | --- |
| Flight | One dated flight with number, airports, registration and times | Reference for bookings and loads | Identified in flight and load messages | — |
| STD / STA | Immutable scheduled departure / arrival | STD helps define the loading period | Included in FLM | — |
| ETD / ETA | Estimates derived from delays | ETD helps end the loading period | Changes trigger MVT-ED / MVT-EA | — |
| ATD / ATA | Actual takeoff / landing times | — | Registration triggers MVT-AD / MVT-AA | — |
| Delay | Minutes, IATA reason and description | — | Estimate changes trigger MVT messages | — |
| Departed | Flight has an ATD | Loading can no longer change | — | — |
| Landed | Flight has an ATA | — | — | — |
| SSM | Schedule message that announces flights | — | — | — |
| ULD | — | Container or pallet with code, weight and destination | Described in a CPM when positioned | — |
| Booked | — | Announced for a flight; not necessarily loaded | — | — |
| Loading position | — | Place on the aircraft deck | Included in a CPM | — |
| Load | — | ULDs assigned to positions | Described in a CPM | — |
| Floor plan | — | Positions for an aircraft type and cargo-door location | — | — |
| Loading period | — | Window when the load may change | — | — |
| Load check | — | Whole-load rule; hard failure blocks finalization, soft failure warns | — | — |
| Finalized load | CPM must be processed before departure | Checked, frozen load | Source of the CPM | — |
| ULD message | — | Incoming announcement of one booking | — | — |
| Telegram (MVT) | Reports estimated or actual flight movement | — | IATA text message | — |
| FLM | Created for a new flight | — | Outgoing Flight Message for partners | — |
| CPM | Receipt and processing unlocks departure | Produced after finalization | Outgoing Containers / Pallets Message for partners | — |
| Operations user | Can view flight information | Can view cargo information | — | User with one of the two planner roles |
| Flight planner | May change delays and record ATD/ATA, never STD/STA | Read-only | — | Role assigned to a user |
| Load planner | Read-only | May position, check and finalize within the permitted state and period | — | Role assigned to a user |

Keycloak supplies identity and roles. Each operational context enforces its own action rules on every access path.

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
