# Air Freight Operations

An airline carries cargo between airports on a fixed flight schedule. Flight planners record delays, their causes, and actual departure and arrival times. Load planners assign booked containers and pallets (ULDs) to aircraft positions, check the load, and finalize it.

Flight schedules and cargo bookings arrive from existing data feeds. The system keeps flight and load information current and creates standard IATA messages for other parties at the appropriate moments, even before any recipients are connected.

## 1. Subdomains

| Subdomain | Type | Why |
| --- | --- | --- |
| Flight Planning | Supporting | It keeps flight times and delays reliable so operations can respond. |
| Freight Loading | Core | Planning, checking and finalising the cargo load is central to this air-freight system. |
| Partner Messaging | Supporting | It creates the required flight and load messages for external parties when relevant events occur. |
| Identity and Access Management (IAM)          | Generic | Signing users in is a common problem handled by Keycloak; each operational context still enforces its own action rules. |

## 2. Bounded contexts

```mermaid
flowchart LR
    subgraph FO["Flight Operations"]
        F["Flight, Delay<br/>STD/STA, ETD/ETA, ATD/ATA"]
    end
    subgraph CL["Cargo Loading"]
        C["ULD booking, Load<br/>Position, Floor Plan, Load Check"]
    end
    subgraph PC["Partner Communication"]
        M["FLM, MVT, CPM<br/>partner delivery"]
    end
    IAM["Identity and Access Management<br/>(external)"]

    F -->|flight identity, state and times| C
    F -->|flight changes| M
    C -->|CPM| F
    C -->|CPM copy| M
    IAM -.->|identity and roles| F
    IAM -.->|identity and roles| C
```

## 3. Ubiquitous language

The same term can have a different role in each context. A dash means the term is not part of that context's model.

| Term | Flight Operations | Cargo Loading | Partner Communication | Identity and Access Management  |
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
| Finalized load | CPM must be processed before departure | Checked, frozen load | Described by its CPM copy | — |
| ULD message | — | Incoming announcement of one booking | — | — |
| Telegram (MVT) | Reports estimated or actual flight movement | — | IATA text message | — |
| FLM | Created for a new flight | — | Outgoing Flight Message for partners | — |
| CPM | Receipt and processing unlocks departure | Created after finalization | Outgoing copy for partners | — |
| Operations user | Can view flight information | Can view cargo information | — | User with one of the two planner roles |
| Flight planner | May change delays and record ATD/ATA, never STD/STA | Read-only | — | Role assigned to a user |
| Load planner | Read-only | May position, check and finalize within the permitted state and period | — | Role assigned to a user |

## 4. Context map

Arrows show dependencies: downstream → upstream.
```mermaid
flowchart LR

    subgraph CL_CTX["Cargo Loading (downstream)"]
        CL["Cargo Loading"]
        FlightPort["FlightInformation<br/>«port»"]
        FlightACL["RestFlightRepository<br/>«anti-corruption layer»"]

        CL --> FlightPort
        FlightPort --> FlightACL
    end

    subgraph FO_CTX["Flight Operations (upstream)"]
        FlightAPI["Flight API<br/>«open host service»"]
        FO["Flight Operations"]
        CPMOut["CPM Integration<br/>«published language»"]

        FO --> CPMOut
    end

    subgraph PC_CTX["Partner Communication"]
        PC["Partner Communication"]
        PartnerACL["Partner Message Translator<br/>«anti-corruption layer»"]

        PC --> PartnerACL
    end

    IAM["Identity and Access Management <br/>external"]

    FlightACL -->|"REST"| FlightAPI

    CPMOut -->|"CPM - RabbitMQ"| CL

    PartnerACL -->|"Flight facts - RabbitMQ"| FO
    PartnerACL -->|"CPM - RabbitMQ"| CL
    FO -.->|"Identity and roles - OIDC"| IAM
    CL -.->|"Identity and roles - OIDC"| IAM
```

| Relationship |Pattern | Why |
| --- | --- | --- |
| Flight Operations → Cargo Loading | Open Host Service, Published Language; Cargo anti-corruption layer | Flight Operations exposes a flight-information contract; Cargo Loading translates it into its own model. |
| Cargo Loading → Flight Operations | Published Language; Flight anti-corruption layer | Cargo Loading publishes CPM; Flight Operations translates it into its departure prerequisite. |
| Flight Operations → Partner Communication | Published Language | Partner Communication uses flight facts to create FLM/MVT notifications. |
| Cargo Loading → Partner Communication | Published Language | Partner Communication receives its own CPM copy for partner delivery. |

## 5. Deployment

```mermaid
flowchart TB
    Client["Web client"] -->|HTTP| F
    Client -->|HTTP| C
    Client -->|OIDC sign-in| IAM["Keycloak<br/>host :8180"]

    subgraph FS["flight-service :8080"]
        F["Flight Operations"]
    end
    subgraph CS["cargo-service :8081"]
        C["Cargo Loading"]
    end
    subgraph PS["partner-communication-service"]
        P["Partner Communication"]
    end
    subgraph PG["One PostgreSQL instance<br/>host :5443 / container :5432"]
        FDB[("Flight database")]
        CDB[("Cargo database")]
        PDB[("Partner messaging database")]
    end

    F --> FDB
    C --> CDB
    P --> PDB
    C -->|Flight API - HTTP :8080| F

    Sources["Schedule and cargo feeds"] -->|SSM and ULD messages| MQ["RabbitMQ<br/>AMQP :5672 / UI :15672"]
    Sim["Simulator<br/>(non-production)"] -->|SSM and ULD messages| MQ
    MQ -->|SSM| F
    MQ -->|ULD message| C
    F -->|flight changes| MQ
    C -->|CPM| MQ
    MQ -->|flight facts and CPM copy| P
    MQ -->|CPM for departure check| F
```

One PostgreSQL instance contains three separate databases, one owned by each service. Services exchange business data through contracts rather than reading another service's database.

## 6. Tactical design: Cargo Loading

`CargoPlan` owns the bookings and placements for one flight and controls finalization.
```mermaid
classDiagram
    class CargoPlan {
        <<AggregateRoot>>
        bookUld(code, weight, destination)
        assignPosition(code, position)
        checkLoad(context)
        finalizeLoad(context)
    }
    class UldBooking {
        <<Entity>>
        code
        weight
        destination
        optionalPosition
    }
    class FlightKey {
        <<ValueObject>>
        flightNumber
        scheduledDepartureDateUTC
        departureAirport
    }
    class UldCode {
        <<ValueObject>>
    }
    class Weight {
        <<ValueObject>>
        value
        unit
    }
    class AirportCode {
        <<ValueObject>>
    }
    class Position {
        <<ValueObject>>
    }
    class CargoPlanState {
        <<enumeration>>
        DRAFT
        FINALIZED
    }
    class FloorPlan {
        positions
        sidesAndRoutes
        cargoDoor
    }
    class LoadCheck {
        <<interface>>
        evaluate(bookings, context)
    }
    class LateralBalanceCheck
    class OffloadingCheck
    class AllCargoLoadedCheck
    class LoadCheckResult {
        <<ValueObject>>
        checkName
        outcome
        failureReason
        hardOrSoft
    }
    class LoadCheckReport {
        <<ValueObject>>
        results
        unpositionedCargo
    }
    CargoPlan --> FlightKey : identity
    CargoPlan --> CargoPlanState : state
    CargoPlan "1" *-- "0..*" UldBooking : bookings
    UldBooking --> UldCode : identity within plan
    UldBooking --> Weight
    UldBooking --> AirportCode : destination
    UldBooking --> Position : optional placement
    CargoPlan ..> FloorPlan : validates placements
    CargoPlan ..> LoadCheck : runs all checks
    LoadCheck <|.. LateralBalanceCheck
    LoadCheck <|.. OffloadingCheck
    LoadCheck <|.. AllCargoLoadedCheck
    CargoPlan ..> LoadCheckReport : returns
    LoadCheck ..> LoadCheckResult : returns
    LoadCheckReport "1" *-- "1..*" LoadCheckResult : results
```

| Building block | Elements |
| --- | --- |
| Aggregate root | `CargoPlan` (owns bookings) |
| Entity | `UldBooking` |
| Value objects | `FlightKey`, `UldCode`, `Weight`, `AirportCode`, `Position`, `LoadCheckResult`, `LoadCheckReport` |
| Reference model | `FloorPlan` |
| Domain checks | `LateralBalanceCheck`, `OffloadingCheck`, `AllCargoLoadedCheck` |
| Domain event | `LoadFinalized` |
| Repository | `CargoPlanRepository` |
| External port | `FlightInformation` |
| Actors | Cargo source, load planner |

Invariants on `CargoPlan`:

1. Unique ULD code per flight; one ULD per valid position, one position per ULD.
2. Booking only while planned. Placement, checking and finalization require a load planner, a planned flight, an open loading period and an unfinalized plan.
3. Finalization reruns all checks; hard failures block it, soft failures warn.
4. Finalized load cannot change.

Loading period: **STD - 12 hours → current ETD - 20 minutes**

### Load checks

| Check | Type | Rule |
| --- | --- | --- |
| Lateral balance | Hard | Heavier side ≤ 110% of lighter side, using a common weight unit; exclude A1, A2, B1 and X. Tolerance is configurable. |
| Offloading | Hard | Destination cargo must reach the door at RL without moving other-destination cargo, following the floor plan's routes and maneuvering rules. |
| Not all cargo loaded | Soft | Warn about bookings without a position. |

- All checks run.
- hard checks inspect positioned cargo only.

### Lifecycle and finalization handoff

```mermaid
stateDiagram-v2
    [*] --> DRAFT: create cargo plan
    DRAFT --> DRAFT: book ULD / assign position / check load
    DRAFT --> FINALIZED: finalize / LoadFinalized
```

- `LoadFinalized` carries the flight key and frozen positioned cargo, used to publish CPM reliably.
- CPM goes via RabbitMQ to Flight Operations and Partner Communication. Departure requires Flight Operations to have received and processed it.

## 7. Tactical design: Flight Operations

_TODO_

## 8. Tactical design: Partner Communication 

`PartnerMessage` holds an outgoing notification and tracks its publication. Flight facts produce FLM/MVT; Cargo's CPM is forwarded to partners.

```mermaid
classDiagram
    class PartnerMessage {
        <<AggregateRoot>>
        markPublished()
    }
    class MessageId {
        <<ValueObject>>
    }
    class MessageContent {
        <<ValueObject>>
        type
        payload
        formatVersion
    }
    class PublicationState {
        <<enumeration>>
        PENDING
        PUBLISHED
    }
    class MessageFormatter {
        <<interface>>
        format(flightFact)
    }
    class FlmFormatter
    class MvtFormatter
    PartnerMessage --> MessageId : identity
    PartnerMessage --> MessageContent : immutable content
    PartnerMessage --> PublicationState : state
    PartnerMessage : sourceMessageId
    PartnerMessage : createdAtUTC
    MessageFormatter <|.. FlmFormatter
    MessageFormatter <|.. MvtFormatter
```

| Building block | Elements |
| --- | --- |
| Aggregate root | `PartnerMessage` |
| Value objects | `MessageId`, `MessageContent` |
| Domain services | `FlmFormatter`, `MvtFormatter` |
| Repository | `PartnerMessageRepository` |
| External port | `PartnerPublisher` |
| Actors | Flight Operations, Cargo Loading, partner systems |


### Message triggers

| Source fact | Outgoing message |
| --- | --- |
| Flight created | FLM (JSON) |
| ETD changed | MVT-ED + MVT-EA (IATA text) |
| ETA only changed | MVT-EA (IATA text) |
| ATD / ATA registered | MVT-AD / MVT-AA (IATA text) |
| CPM received from Cargo | CPM copy (JSON) |

### Publication lifecycle

```mermaid
stateDiagram-v2
    [*] --> PENDING: create notification
    PENDING --> PENDING: publication fails / retry
    PENDING --> PUBLISHED: transport confirms publication
```

- `PUBLISHED` means transport confirmation.

## 9. Layering 

_TODO_

## 10. Running it 

_TODO_
