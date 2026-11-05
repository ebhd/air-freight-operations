# Air Freight Operations

An airline carries cargo between airports on a fixed flight schedule. Flight planners record delays, their causes, and actual departure and arrival times. Load planners assign booked containers and pallets (ULDs) to aircraft positions, check the load, and finalize it.

Flight schedules and cargo bookings arrive from existing data feeds. The system keeps flight and load information current and creates standard IATA messages for other parties at the appropriate moments, even before any recipients are connected.

## 1. Subdomains


| Subdomain                            | Type       | Rationale                                                                                                               |
| ------------------------------------ | ---------- | ----------------------------------------------------------------------------------------------------------------------- |
| Flight Planning                      | Supporting | It keeps flight times and delays reliable so operations can respond.                                                    |
| Freight Loading                      | Core       | Planning, checking and finalising the cargo load is central to this air-freight system.                                 |
| Identity and Access Management (IAM) | Generic    | Signing users in is a common problem handled by Keycloak; each operational context still enforces its own action rules. |

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
    MF["Message Formatter<br/>FLM, MVT, CPM"]
    F -->|flight changes - published language| MF
    MF -->|prepared messages - sender ACL| M
    C -->|finalized load - published language| F
    C -->|finalized load - published language| MF
    IAM -.->|identity and roles| F
    IAM -.->|identity and roles| C
```

## 3. Ubiquitous language


| Term             | Flight Operations                                                                     | Cargo Loading                                                                                                                       | Message Formatter / Partner Communication          |
| ---------------- | ------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------- |
| Flight           | Aggregate owning times, status and delays for one dated flight                        | Flight information used for bookings and load planning                                                                              | Flight identity and facts used to prepare messages |
| FlightKey        | Flight number, UTC scheduled departure date and departure airport code                | Identifies the flight of a CargoPlan                                                                                                | Identifies the flight described by a message       |
| Airport          | Reference with IATA code and display name                                             | Flight arrival and ULD destination codes used to check offloading                                                                   | Airport code included in messages                  |
| STD / STA        | Immutable scheduled departure / arrival times                                         | STD anchors the loading-period start                                                                                                | Scheduled times included in FLM                    |
| ETD / ETA        | Estimated departure / arrival times calculated from delays                            | ETD determines the loading-period end                                                                                               | Estimated times included in MVT-ED / MVT-EA        |
| ATD / ATA        | Recorded actual departure / arrival times                                             | —                                                                                                                                  | Actual times included in MVT-AD / MVT-AA           |
| ULD              | —                                                                                    | Unit Load Device (container or pallet) booked on a flight, with code, weight and destination; placement is optional                 | Positioned ULD details included in CPM             |
| Weight           | —                                                                                    | Value and unit used for cargo calculations                                                                                          | ULD weight and unit included in CPM                |
| Loading position | —                                                                                    | Named place on the aircraft deck                                                                                                    | ULD position included in CPM                       |
| Loading window   | —                                                                                    | Period from STD - 12 hours to current ETD - 20 minutes in which a planned, unfinalized load may change; recomputed for every action | —                                                 |
| Finalized load   | Processed CPM confirms the cargo prerequisite for departure                           | Checked, frozen load                                                                                                                | Load snapshot carried in CPM                       |
| CPM              | Received load message whose processing satisfies the cargo prerequisite for departure | Containers / Pallets Message describing the finalized load                                                                          | Copy forwarded to partners                         |

## 4. Context map

```mermaid
flowchart LR

    subgraph CL_CTX["Cargo Loading (downstream)"]
        CL["Cargo Loading"]
        FlightACL["Flight copy + FlightOperationsClient<br/>«anti-corruption layer»"]

        CL --> FlightACL
    end

    subgraph FO_CTX["Flight Operations (upstream)"]
        FlightAPI["Flight API<br/>«open host service»"]
        FO["Flight Operations"]
        CPMReceiver["CPM Receiver<br/>«anti-corruption layer»"]

        FO --> CPMReceiver
    end

    Planning["Planning system<br/>(simulator in this project)"]

    subgraph MF_CTX["Message Formatter"]
        FlightFormatter["Flight and Cargo event listeners and formatters"]
    end
    subgraph PC_CTX["Partner Communication"]
        PreparedACL["Prepared-message listener<br/>«anti-corruption layer»"]
        PC["Partner Communication"]
        PreparedACL --> PC
    end

    IAM["Identity and Access Management <br/>external"]

    FlightACL -->|"REST, only a flight not in the copy"| FlightAPI
    FlightACL -->|"Flight facts - RabbitMQ<br/>«published language»"| FO

    CPMReceiver -->|"Finalized load cargo.load-finalized - RabbitMQ<br/>«published language»"| CL

    FO -->|"flight.replanned - RabbitMQ<br/>«published language»"| Planning
    Planning -->|"SSM for the new flight - RabbitMQ"| FO
    FO -->|"Flight facts - RabbitMQ<br/>«published language»"| FlightFormatter
    CL -->|"Finalized load cargo.load-finalized - RabbitMQ<br/>«published language»"| FlightFormatter
    FlightFormatter -->|"Prepared messages - RabbitMQ"| PreparedACL
    FO -.->|"Identity and roles - OIDC"| IAM
    CL -.->|"Identity and roles - OIDC"| IAM
```


| Relationship                                           | Pattern                                                                                                      | Why                                                                                                                                                                                                           |
| ------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Flight Operations -> Cargo Loading                     | Published Language (flight events) and Open Host Service for one unknown flight; Cargo anti-corruption layer | Cargo keeps its own copy of the flights it needs, so it keeps working while Flight is down, and asks the API only for a flight that is not in the copy yet. Both paths are translated into Cargo's own model. |
| Cargo Loading -> Flight Operations                     | Published Language; Flight anti-corruption layer                                                             | Cargo Loading publishes the finalized load (`cargo.load-finalized`); Flight's `CpmReceiver` translates it into its departure prerequisite.                                                                    |
| Flight Operations / Cargo Loading -> Message Formatter | Published Language                                                                                           | Formatter consumes Flight facts for FLM/MVT and Cargo's finalized load for the partner CPM. Cargo never formats the partner CPM.                                                                              |
| Message Formatter -> Partner Communication             | Sender anti-corruption layer                                                                                 | Sender translates prepared batches into its own message model, persists them and publishes them.                                                                                                              |
| Flight Operations -> Planning system                   | Published Language (`flight.replanned`), Conformist back                                                     | Flight ends the old flight and publishes the proposed schedule; the planning system (the simulator here) answers with a normal SSM, so the new flight enters like every other flight.                         |

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

    MF["message-formatter-service :8084<br/>stateless"]
    F --> FDB
    C -->|plan and outbox in one transaction| CDB
    P --> PDB
    C -->|Flight API - HTTP :8080, only a flight not in the copy| F

    Sim["Simulator<br/>(non-production)"] -->|SSM and ULD messages| MQ["RabbitMQ<br/>AMQP :5672 / UI :15672"]
    MQ -->|flight.replanned - planning.flight-replanned| Sim
    MQ -->|SSM| F
    MQ -->|ULD message and flight changes| C
    F -->|flight changes| MQ
    C -->|finalized load - outbox, cargo.events| MQ
    MQ -->|flight facts| MF
    MF -->|prepared batch - formatter.events| MQ
    MQ -->|prepared batch - sender ACL| P
    MQ -->|finalized load - partner CPM| MF
    MQ -->|finalized load - departure condition| F

    IAM --> KDB[("Keycloak MySQL database<br/>host :3307")]
    F -.->|signing keys| IAM
    C -.->|signing keys and service token| IAM
```

- One PostgreSQL instance hosts three databases, and each service only accesses its own. Services share information through APIs or RabbitMQ.
- Flight and Cargo each have an `outbox_messages` table: an event is written in the same transaction as the change and sent after the commit with publisher confirms. A scheduled job resends what is pending with the same message ID.
- The simulator is not deployed. It also plays the external planning system for replanning.
- Keycloak keeps its realm, clients and users in its own MySQL database.

## 6. Tactical design: Cargo Loading

```mermaid
classDiagram
    class CargoPlan {
        <<AggregateRoot>>
        bookUld(code, weight, destination, flight)
        loadingRefusal(context)
        requireLoadingAllowed(context)
        assignPosition(code, position, context)
        clearPosition(code, context)
        checkLoad(context, checks)
        finalizeLoad(context, checks)
        state
        finalizedAt
    }
    class LoadingContext {
        <<ValueObject>>
        flight
        window
        now
    }
    class LoadingWindow {
        <<ValueObject>>
        opensAt
        closesAt
    }
    class LoadingWindowPolicy {
        <<Policy>>
        opensBeforeStd
        closesBeforeEtd
        windowFor(std, etd)
    }
    class LoadingRefusal {
        <<enumeration>>
        PLAN_FINALIZED
        FLIGHT_NOT_PLANNED
        WINDOW_NOT_OPEN_YET
        WINDOW_CLOSED
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
        scheduledDepartureDate
        departureAirportCode
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
    class LoadingPosition {
        <<ValueObject>>
        row
        side
    }
    class CargoPlanState {
        <<enumeration>>
        DRAFT
        FINALIZED
        REPLANNED
    }
    class LoadChecks {
        <<DomainService>>
        run(bookings, arrivalAirport)
    }
    class LoadCheck {
        <<interface>>
        evaluate(bookings, arrivalAirport)
    }
    class LateralBalanceCheck {
        <<DomainService>>
        tolerance
        balance(bookings)
    }
    class OffloadingCheck {
        <<DomainService>>
        simulate(bookings, arrivalAirport)
    }
    class OffloadingResult {
        <<ValueObject>>
        unloadingOrder
        blockedPositions
        passed()
    }
    class AllCargoLoadedCheck {
        <<DomainService>>
    }
    class LoadCheckResult {
        <<ValueObject>>
        name
        severity
        passed
        reason
    }
    class LoadCheckReport {
        <<ValueObject>>
        results
        hasHardFailure()
    }
    class LoadFinalized {
        <<DomainEvent>>
        flightKey
        finalizedAt
        positionedUlds
        report
    }
    CargoPlan --> FlightKey : identity
    CargoPlan --> CargoPlanState : state
    CargoPlan ..> LoadingContext : judges actions on
    CargoPlan ..> LoadingRefusal : returns
    LoadingContext --> LoadingWindow
    LoadingWindowPolicy ..> LoadingWindow : creates
    CargoPlan "1" *-- "0..*" UldBooking : bookings
    UldBooking --> UldCode : identity within plan
    UldBooking --> Weight
    UldBooking --> AirportCode : destination
    UldBooking --> "0..1" LoadingPosition : optional placement
    CargoPlan ..> LoadChecks : runs all checks
    LoadChecks --> "3" LoadCheck : runs every check
    LoadCheck <|.. LateralBalanceCheck
    LoadCheck <|.. OffloadingCheck
    LoadCheck <|.. AllCargoLoadedCheck
    OffloadingCheck ..> OffloadingResult : returns
    LoadChecks ..> LoadCheckReport : returns
    CargoPlan ..> LoadFinalized : finalizeLoad returns
    LoadCheckReport "1" *-- "3" LoadCheckResult : results
```


| Building block | Elements                                                                                                                                                                          |
| -------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Aggregate root | `CargoPlan` (owns bookings)                                                                                                                                                       |
| Entity         | `UldBooking`                                                                                                                                                                      |
| Value objects  | `FlightKey`, `UldCode`, `Weight`, `AirportCode`, `LoadingPosition`, `LoadingWindow`, `LoadingContext`, `OffloadingResult`, `LateralBalance`, `LoadCheckResult`, `LoadCheckReport` |
| Policy         | `LoadingWindowPolicy` (configurable offsets); lateral balance tolerance (configurable)                                                                                            |
| Domain checks  | `LoadChecks` runs the `LoadCheck` implementations `LateralBalanceCheck`, `OffloadingCheck` and `AllCargoLoadedCheck` (domain services)                                            |
| Domain event   | `LoadFinalized`                                                                                                                                                                   |
| Repository     | `CargoPlanRepository`; `FlightRepository` and `FlightCopyRepository` for Cargo's copy of the flights                                                                              |
| Actors         | Load planner                                                                                                                                                                      |

Invariants on `CargoPlan`:

1. Unique ULD code per flight; one ULD per valid position, one position per ULD.
2. Booking only while planned. Placement, checking and finalization need a planned flight, an open loading period and an unfinalized plan (`requireLoadingAllowed`); the role is checked before, by Spring Security.
3. Finalization reruns all checks; hard failures block it, soft failures warn.
4. Finalized load cannot change.
5. The cargo of a replanned flight moves once to the plan of the new flight: bookings keep their position unless it is taken there, and the new plan is a `DRAFT`, also after a finalized load, so the load planner checks and finalizes again. The old plan ends as `REPLANNED` with `movedTo`.

Every change raises the version of the `cargo_plans` row (optimistic locking): of two overlapping changes the second is rolled back with 409, and a unique constraint on (flight, position) guards invariant 1 in the database. A ULD moves to a free position in one step; swapping two ULDs takes two (team reading).

`LoadingPosition` is an enum of the 34 main-deck positions of the Boeing 747-400 freighter. A second aircraft type would introduce a floor plan per type. Every check implements `LoadCheck` and gets the bookings and the arrival airport, also when it does not use the airport. `LoadChecks` runs its list in a fixed order and builds one report, so a new check is one new class added to that list.

Loading period: **STD - 12 hours → current ETD - 20 minutes**

- Both offsets are settings, and only STD and ETD count, so arrival times cannot move the window.
- The window is half open: open at the opening instant, closed at the closing instant (team reading).
- It is never stored. A `LoadingContext` is built per action from Flight's current data, so a departure delay moves the end at the next action.

### Load checks


| Check                | Type | Rule                                                                                                                                         |
| -------------------- | ---- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| Lateral balance      | Hard | Heavier side ≤ 110% of lighter side, using a common weight unit; exclude A1, A2, B1 and X. Tolerance is configurable.                       |
| Offloading           | Hard | Destination cargo must reach the door at RL without moving other-destination cargo, following the floor plan's routes and maneuvering rules. |
| Not all cargo loaded | Soft | Warn about bookings without a position.                                                                                                      |

- All checks run, also after an earlier one failed, and every result has a name, type, outcome and reason. Checking changes nothing.
- Hard checks inspect positioned cargo only.
- A check is refused outside the window and after finalizing (team reading: Check Load is a loading action).

Lateral balance:

- Left and right are the sums of the L and R positions, compared in kg, the unit we chose (the course names none). LB uses the exact factor 0.45359237.
- `heavier <= lighter × (1 + tolerance)` with exact decimals: 1000/1100 passes, 1000/1101 fails.
- From the formula, not a course case: an empty load passes and cargo on only one side fails.
- The tolerance is a setting, default `0.10`; changing it needs a restart, not a release.

Offloading:

- `OffloadingCheck` simulates unloading on its own copy of the positions. In passes from nose to tail it removes every ULD for the arrival airport whose route is empty; what remains is blocked. Cargo for other airports never moves.
- Routes follow the order of `LoadingPosition`: along the own side to row R, leaving through RL, and right cargo crosses from RR to RL. A route never switches sides.
- A1, A2, B1 and X have no route of their own and maneuver first: A1, A2 and B1 need CL and CR empty, X needs UL and UR empty. Then one clear side route is enough.
- Without destination cargo aboard the check passes. A1, A2 and B1 do not block each other (team reading; open question for the coach).

### Lifecycle and finalization handoff

```mermaid
stateDiagram-v2
    [*] --> DRAFT: create cargo plan
    DRAFT --> DRAFT: book ULD / assign position / check load
    DRAFT --> FINALIZED: finalize / LoadFinalized
    DRAFT --> REPLANNED: flight.replaced / cargo moves
    FINALIZED --> REPLANNED: flight.replaced / cargo moves
    DRAFT --> [*]: flight.removed
    FINALIZED --> [*]: flight.removed
    REPLANNED --> [*]: flight.removed
```

- A departure delay never reopens a finalized plan; there is no unfinalize.
- `finalizeLoad` reruns all checks. A hard failure changes nothing (409 `HARD_CHECKS_FAILED` with the report); soft warnings do not block. A plan without positioned cargo can be finalized, with an empty ULD list.
- After finalizing, positions, checks and a second finalize refuse with `PLAN_FINALIZED`. A new ULD booking goes to `rejected_messages`; a repeat of an existing booking stays an ignored duplicate (team reading).
- `LoadFinalized` is written to the outbox in the same transaction and sent as `cargo.load-finalized` on `cargo.events` ([contract](../README.md#messages)). Flight (departure condition) and the Formatter (partner CPM) each bind their own queue. Cargo never formats the partner CPM.
- Team reading: Flight's processing of `cargo.load-finalized` is the receipt of the CPM. A resend keeps its message ID, so a duplicate has no second effect in Flight or Partner.

## 7. Tactical design: Flight Operations

```mermaid
classDiagram
    direction TB

    class Flight {
        <<AggregateRoot>>
        arrivalAirportCode
        aircraftRegistration
        scheduledDeparture
        scheduledArrival
        actualDeparture
        actualArrival
        finalizedLoadProcessed
        replanning
        replacementCreated
        replaces
        create(flightKey, arrivalAirportCode, aircraftRegistration, scheduledDeparture, scheduledArrival)
        addDepartureDelay(minutes, reasonCode, description)
        addArrivalDelay(minutes, reasonCode, description)
        updateDepartureDelay(delayId, minutes, description)
        updateArrivalDelay(delayId, minutes, description)
        removeDepartureDelay(delayId)
        removeArrivalDelay(delayId)
        estimatedDeparture()
        estimatedArrival()
        recordFinalizedLoad()
        proposeReplanning(delayType, delayId, minutes, reasonCode, description, newFlightNumber, now)
        replan(delayType, delayId, minutes, reasonCode, description, newFlightNumber, now)
        recordReplacementCreated()
        recordReplaces(replannedFlight)
        recordDeparture(actualDeparture, now, policy)
        recordArrival(actualArrival, now, policy)
    }

    class ActualTimePolicy {
        <<ValueObject>>
        tolerance
        latestAllowed(estimate)
    }

    class ActualTimeRefusal {
        <<enumeration>>
        NOT_PLANNED
        NOT_DEPARTED
        CPM_NOT_PROCESSED
        IN_THE_FUTURE
        NOT_AFTER_DEPARTURE
        TOO_LATE
    }

    class Replanning {
        <<ValueObject>>
        newFlightNumber
        newScheduledDeparture
        newScheduledArrival
        delayType
        minutes
        reasonCode
        description
        replannedAt
    }

    class FlightKey {
        <<ValueObject>>
        flightNumber
        scheduledDepartureDate
        departureAirportCode
    }

    class FlightStatus {
        <<enumeration>>
        PLANNED
        DEPARTED
        LANDED
        REPLANNED
    }

    class DelayType {
        <<enumeration>>
        DEPARTURE
        ARRIVAL
    }

    class Delay {
        <<Entity>>
        id
        minutes
        reasonCode
        description
    }




    Flight "1" *-- "0..*" Delay : departureDelays
    Flight "1" *-- "0..*" Delay : arrivalDelays
    Flight "1" --> "1" FlightKey : key
    Flight --> "1" FlightStatus : status
    Flight ..> ActualTimePolicy : checks ATD and ATA with
    Flight ..> ActualTimeRefusal : refuses with
    Flight --> "0..1" Replanning : replanning
    Flight --> "0..1" FlightKey : replaces
    Replanning --> "1" DelayType : delayType

```


| Building block | Elements                                                                                                                          |
| -------------- | --------------------------------------------------------------------------------------------------------------------------------- |
| Aggregate root | `Flight` (owns delays and controls the lifecycle)                                                                                 |
| Entity         | `Delay`                                                                                                                           |
| Value objects  | `FlightKey`, `ActualTimePolicy`, `RetentionPolicy`, `Replanning` (a delay's `id` is a plain `UUID`, no value object)              |
| Enumerations   | `FlightStatus`, `DelayType`, `ActualTimeRefusal`                                                                                  |
| Reference data | `DelayReason`, `Airport` (an unlisted code is shown without a name)                                                               |
| Domain events  | `FlightCreated`, `FlightEstimatesChanged`, `FlightDeparted`, `FlightLanded`, `FlightReplanned`, `FlightReplaced`, `FlightRemoved` |
| Repositories   | `FlightRepository`, `DelayReasonRepository`, `AirportRepository`                                                                  |
| Actors         | Flight planner, Load planner                                                                                                      |

Invariants on `Flight`:

1. `FlightKey`, scheduled departure and scheduled arrival cannot change. The key's `scheduledDepartureDate` is the UTC date of the original scheduled departure. Flight numbers contain 1–32 letters or digits, matching Cargo's accepted format.
2. A flight moves only from `PLANNED` to `DEPARTED` to `LANDED`, or from `PLANNED` to `REPLANNED`. `REPLANNED` is final: no delay changes, no second replanning, no CPM, no departure.
3. Departure delays can change only while planned, arrival delays can change while planned or departed. All delays freeze after landing and after replanning.
4. Each delay is 1–720 minutes, and all delays of a flight together (departure and arrival) are at most 720 minutes. A change that would pass that total is refused with 409 `TOTAL_DELAY_EXCEEDED`. ETD and ETA are calculated from the current delays, never entered manually.
5. Actual times are registered once and never change; a second registration is refused.
6. ATD needs the processed CPM, is at most `now` and at most ETD plus the tolerance; ATA is later than ATD, at most `now` and at most ETA plus the tolerance. The limit itself is accepted. The tolerance (`ActualTimePolicy`, 5 minutes) is configuration; `now` comes from the service's clock, so the aggregate stays testable.

### Flight workflow

- SSM creates `PLANNED` flights without delays or actual times. Duplicate keys are logged and skipped; the remaining dates continue. An invalid SSM creates nothing: after the retries `FlightMessageRecoverer` stores it in `rejected_messages` and dead-letters it.
- `CpmReceiver` (anti-corruption layer) checks `cargo.load-finalized`. `FinalizedLoadService` locks the flight row, skips a known message ID and records the load in one transaction.
- `recordDeparture` refuses with `CPM_NOT_PROCESSED` until the CPM is processed. Both lock the same row, so they never interleave.
- A registered ATD or ATA goes through the outbox as `FlightDeparted` or `FlightLanded`. A refused registration publishes nothing.

### Replanning (out of scope, built anyway)

The course says a flight held for more than twelve hours is replanned outside this system. We still let a planner start it, because otherwise such a flight is stuck.

- A refused delay change (409 `TOTAL_DELAY_EXCEEDED`) carries a proposal. Only an explicit `POST .../replanning` replans.
- The new flight gets the old number plus the next free letter (HJ500 → HJ500A), and times that include all delays.
- `flight.replanned` asks the planning system for a normal SSM. When that creates the new flight, Flight publishes `flight.replaced`, and only then does Cargo move the cargo. If no new flight comes, the old flight waits (documented limitation).

### Flight lifecycle

```mermaid
stateDiagram-v2
    [*] --> PLANNED: create flight from SSM
    PLANNED --> PLANNED: add / edit / remove departure or arrival delay (total at most 720 min)
    PLANNED --> PLANNED: process finalized-load CPM
    PLANNED --> REPLANNED: replan (flight planner confirms, total would exceed 720 min)
    PLANNED --> DEPARTED: recordDeparture(ATD) — CPM processed, ATD ≤ now, ATD ≤ ETD + tolerance
    DEPARTED --> DEPARTED: add / edit / remove arrival delay (total at most 720 min)
    DEPARTED --> LANDED: recordArrival(ATA) — ATA > ATD, ATA ≤ now, ATA ≤ ETA + tolerance
    REPLANNED --> REPLANNED: replacement flight created (link set once, no state change)
    PLANNED --> [*]: retention
    DEPARTED --> [*]: retention
    REPLANNED --> [*]: retention
    LANDED --> [*]: retention
```

### Retention

Flight owns the rule and its setting. `FlightCleanupJob` removes a flight with its delays and writes `flight.removed` to the outbox in one transaction. Cargo then removes the plan, bookings, ULD dedup rows and flight copy, also when it was down at that moment. Until then Cargo still shows the flight from its copy.

- Cut-off: 00:00 UTC on the key date plus the retention, in any status. With 7 days a flight of 29 October expires on 5 November at 00:00 UTC.
- Never touched: pending outbox rows, `PENDING` partner messages and queues. Published outbox rows expire like a flight, so Cargo needs no retention setting.
- Stay: `rejected_messages`, the DLQs, logs, `message_receipts` and `partner_messages`.
- A late SSM is handled like any other: an expired flight is created and goes at the next run.

## 8. Tactical design: Message Formatter and Partner Communication

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
    note for MessageFormatter "one implementation per message type, in Message Formatter"
    class PreparedMessageListener {
        <<Sender ACL>>
    }
    PreparedMessageListener --> MessageContent : translates prepared batch
    PartnerMessage --> MessageId : identity
    PartnerMessage --> MessageContent : immutable content
    PartnerMessage --> PublicationState : state
    PartnerMessage : sourceMessageId
    PartnerMessage : createdAtUTC
```


| Building block            | Elements                                                                                                                                                |
| ------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Aggregate root            | `PartnerMessage`                                                                                                                                        |
| Value objects             | `MessageId`, `MessageContent`                                                                                                                           |
| Domain rule               | `MvtTimeRule`                                                                                                                                           |
| Formatter implementations | `FlmFormatter`, `MvtEdFormatter`, `MvtEaFormatter`, `MvtAdFormatter`, `MvtAaFormatter`, `CpmFormatter`, `FlightReplannedFormatter` in Message Formatter |
| Repository                | `PartnerMessageRepository`                                                                                                                              |
| Actors                    | Partners                                                                                                                                                |

### Message triggers

| Source fact | Outgoing message |
| --- | --- |
| Flight created | FLM (JSON) |
| ETD changed | MVT-ED + MVT-EA (IATA text) |
| ETA only changed | MVT-EA (IATA text) |
| ATD / ATA registered | MVT-AD / MVT-AA (IATA text); line 2 keeps the scheduled departure day |
| Load finalized in Cargo | CPM (JSON) with only the positioned ULDs, see the [contract](../README.md#messages) |
| New flight of a replanning exists | FLIGHT_REPLANNED (JSON, our own message): old and new flight, new STD and STA. The new flight also gets its normal FLM |

### Reading an MVT time

Line 3 holds only `HHmm` in UTC. `MvtTimeRule` picks the instant within 12 hours of the stored first reading (`createdAtUTC`), so a redelivery gives the same result. Exactly 12 hours away counts as the later side (team reading). Only tests call it, because partner telegrams are a later extension.

### Publication lifecycle

A `PartnerMessage` stays `PENDING` until the transport confirms it, then becomes `PUBLISHED`. A failed publication is retried.

## 9. Layering and cross-cutting rules

Each service uses `domain` for business rules and repository interfaces, `application` for use cases, `infrastructure` for persistence and external adapters, and `api` for REST controllers and DTOs. A service serves its own pages from its JAR; they call its REST API with `fetch` and a bearer token.

Sign-in goes through Keycloak (OpenID Connect). Flight and Cargo check every JWT themselves and map the realm roles to Spring roles in `SecurityConfiguration`. Both roles may read; changes need `flight-planner` in Flight and `load-planner` in Cargo. The pages hide actions the role may not use, but the server decides. Cargo reads Flight with its own client-credentials token.

Times are UTC everywhere except on the screen, where the pages show Brussels time. The flight key date is the UTC date of STD, and the day overviews filter on it. Planners enter actual times in Brussels time, and the page refuses nonexistent or ambiguous times around a clock change.

Every message has a stable ID, so a redelivery has no second effect. Cargo and Flight store an invalid message in `rejected_messages`; temporary database failures are requeued.

### Cargo's flight copy

Cargo reads flights from its own `flights` table, kept up to date by flight events. Only a flight that is not in the copy is fetched once over HTTP.

- Schedule and estimates only change for a newer `last_change_at`, and the status only moves forward (`advanceStatus`), so a late or repeated event changes nothing.
- Flight stamps an event before its commit, so an HTTP lookup stores the time before the call minus five seconds (`COMMIT_MARGIN`). The event of a change the answer missed then still counts as newer. This assumes the clocks differ by less than that, which holds in Docker on one machine.
- The copy is a read model without business rules, so it uses `JdbcClient`: JPA cannot express the conditional upsert.
