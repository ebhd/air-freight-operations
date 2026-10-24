# Air Freight Operations

Java 22 · Spring Boot 4.1.1 · Gradle 9.7.1 multi-project build.

| Service | HTTP port | PostgreSQL database |
| --- | --- | --- |
| `flight-service` | 8080 | `flight` |
| `cargo-service` | 8081 | `cargo` |
| `partner-communication-service` | 8082 | `partner_communication` |
| `simulator`  | 8083 | None |

The three business services use `domain`, `application`, `infrastructure` and `api` packages. Current scope: infrastructure connectivity, health checks, bearer-token authentication, Flight's US-3 SSM ingestion and a local SSM and ULD simulator (see [Local development](#local-development)). Flight exposes a flight lookup by key and a day overview (`GET /api/flights?date=`), which Cargo calls with a service token. Cargo serves the day overview to the load planner on its own `GET /api/flights?date=`, which hides departed and landed flights unless `includeDeparted=true`, and answers 503 when Flight is unreachable. `GET /api/cargo-plans/{flightNumber}/{scheduledDepartureDate}/{departureAirportCode}` returns one flight's data and status from Flight with all its ULD bookings and their loading position (`null` without one), also after departure: 404 for a flight Flight does not know, 400 for an invalid key, 503 when Flight is unreachable. `GET /api/cargo-plans/{flightNumber}/{scheduledDepartureDate}/{departureAirportCode}/loading-window` returns the flight's loading window and whether loading is allowed now; see [Loading window](#loading-window). The load planner puts booked ULDs on the 34 main-deck positions; see [Loading positions](#loading-positions). Cargo also serves the load planner's "Flights to load" page at `http://localhost:8081/`, which links to each flight's "Cargo plan" page (`cargo-plan.html`); both pages are written in TypeScript with Tailwind CSS and compiled by the Gradle build (see [Cargo frontend](#cargo-frontend)). Cargo books ULDs received on RabbitMQ onto planned flights; see [ULD bookings](#uld-bookings). Flight uses a plain domain model with separate JPA entities and a database adapter. Other business workflows and Flight's web UI are not implemented.

## Run

Requires Docker with Compose. First start Keycloak, configure the realm through its admin console and set Cargo's
generated client secret in `.env`. Then start the applications:

```sh
docker compose up -d --build --wait
docker compose ps
docker compose down
```

`--wait` waits for healthy containers. `/actuator/health` on each service checks PostgreSQL and RabbitMQ; Cargo also checks Flight over HTTP. Dependencies are checked at startup, without automatic restart after later outages.

Named volumes preserve data after shutdown. PostgreSQL initializes the three databases only on the first start with an empty volume.

Each service creates its tables from its `schema.sql` (`CREATE TABLE IF NOT EXISTS`) and Hibernate validates them at startup; there is no migration tool. A schema change to an existing table therefore needs that service's database to be recreated. Cargo's loading positions (#20) are such a change: a new `cargo_plans` table and a new `uld_bookings.loading_position` column. With an older `cargo` database Cargo stops at startup with a schema validation error. Recreate only Cargo's database (flights, RabbitMQ and Keycloak stay; Cargo's bookings are lost, so publish them again):

```sh
docker compose stop cargo-service
docker compose exec app-postgres psql -U user -d postgres -c 'DROP DATABASE cargo WITH (FORCE)' -c 'CREATE DATABASE cargo'
docker compose up -d --build --wait cargo-service
```

Or reset everything with `docker compose down -v` (also flights and the Keycloak
realm). Reconfigure Keycloak through its admin console, update the generated
client secrets in your local configuration, then start the applications and
recreate flights and bookings with the simulators.

## Local development

Import the root Gradle project with JDK 22. Start infrastructure, then run the application main classes from the IDE:

```sh
docker compose up -d --wait app-postgres app-rabbitmq keycloak
./gradlew bootJar
```

| Infrastructure | Local address | Username / password |
| --- | --- | --- |
| PostgreSQL | `localhost:5443` | `user` / `password` |
| RabbitMQ | `localhost:5672` | `user` / `password` |
| RabbitMQ management | `http://localhost:15672` | `user` / `password` |
| Keycloak | `http://localhost:8180` | `admin` / `admin` |

Development only: demo infrastructure credentials and Keycloak `start-dev`.
Keycloak stores its configuration in a separate MySQL 8.4.13 database, available at
`localhost:3307` (`keycloak` / `password`). Configure the realm and clients through
the admin console before running the applications. For an
IDE run, set `CARGO_SERVICE_CLIENT_SECRET` in Cargo's run configuration; `.env`
is read by Docker Compose only.

Configuration lives in each service's `application.properties`; standard Spring environment variables override it. Compose sets container hostnames and Cargo's `FLIGHT_API_URL`.

### Cargo frontend

Page HTML lives in `cargo-service/src/main/resources/static/`, TypeScript modules in `cargo-service/src/main/typescript/` and the Tailwind CSS input in `cargo-service/src/main/css/app.css`. Gradle's `processResources` (part of `bootJar`, `bootRun` and `check`) type-checks TypeScript, bundles the page modules and `keycloak-js` with Vite, and runs the Tailwind CLI. The JAR contains the page entry scripts and shared chunks under `static/js/`, and `static/css/app.css`. The Gradle Node plugin downloads the pinned Node version and runs `npm ci` in `cargo-service/`, so no Node installation is needed, also not in Docker or CI. A TypeScript type error fails the build. Compiled files are not committed; edit the sources. Frontend Keycloak settings are build-time values: `VITE_KEYCLOAK_URL`, `VITE_KEYCLOAK_REALM` and `VITE_KEYCLOAK_CLIENT_ID`. Docker Compose passes them from `.env` to the Cargo image build.

For manual SSM and ULD submission, start Flight Service and Cargo Service and run `./gradlew :simulator:bootRun`. Then send the requests in [simulator/http/ssm.http](simulator/http/ssm.http) and [simulator/http/uld.http](simulator/http/uld.http). The simulator listens on 127.0.0.1:8083, has no database and is not part of the Compose deployment.

## ULD bookings

Cargo consumes ULD bookings from the durable `cargo.uld` queue. The official ULD JSON schema has not been published (Q01), so this is the team's provisional contract:

```json
{
  "messageId": "uld-0001",
  "version": 1,
  "flightNumber": "HJ221",
  "scheduledDepartureDay": 19,
  "departureAirportCode": "BRU",
  "uldCode": "AKE12345KL",
  "weight": { "value": 1250.5, "unit": "KG" },
  "destination": "AMS"
}
```

- The AMQP `message_id` property identifies the message; `messageId` in the body is used when it is absent.
- `version` is required and must be `1`. A message without it or with another version is stored in `rejected_messages`.
- The day of month is resolved to the nearest date relative to when the message ID was first received (`message_receipts`), preferring the future on a tie (team interpretation of Q02). Retries and requeues reuse that instant. Weight units are `KG` or `LB` (Q15).
- The flight must exist in Flight and be planned. A ULD code already booked on that flight is ignored and logged, even when weight or destination differ; the existing booking never changes. Differing content is logged as a warning.
- Two deliveries processed at the same time cannot book the same ULD twice: the database rejects the second insert, and its retry records it as a duplicate.
- The first booking of a flight creates its row in `cargo_plans` (version 0); every booking raises that version, like every position change (see [Loading positions](#loading-positions)). A booking that overlaps a position change on the same flight fails at commit; it is retried and the retry, which reads the new version, succeeds.
- A redelivered message ID has no second effect: `processed_messages` keeps the first result.
- Unreadable or invalid messages, unknown flights and flights that are no longer planned are stored in `rejected_messages` with their content and reason; a redelivered rejected message ID stays rejected. Database or Flight outages are retried and the message is requeued, not dropped.

### Simulating ULD bookings

`POST http://localhost:8083/api/simulator/uld-bookings` takes a JSON array of one or more records and publishes one ULD message per record to `cargo.uld`. Examples: [simulator/http/uld.http](simulator/http/uld.http).

1. Start Flight and Cargo, then the simulator. Cargo declares `cargo.uld`; before that queue exists, RabbitMQ drops published messages.
2. **Process the SSM first.** Send the SSM and wait until `GET http://localhost:8081/api/flights?date=<UTC date>` lists the flight (load-planner token, see `http/local.http`). A 202 from the SSM request only means it was published. A ULD for a flight that does not exist yet is rejected for good; send it again with a new message ID afterwards.
3. Send the ULD records. `scheduledDepartureDate` is the flight's full UTC scheduled departure date (`yyyy-MM-dd`), as in the flight key. The message carries only its day of month, and Cargo picks the nearest date with that day when it processes the message, so use a date within two weeks of today.

Records use the contract fields above, with `scheduledDepartureDate` instead of `scheduledDepartureDay` and without `version` (the simulator sends `1`). `messageId` is optional: without it, the simulator generates a UUID. The same ID goes into the AMQP `message_id` property; messages are persistent.

- 202 `{"messageIds": [...]}`: published, one ID per record in request order. Not a booking confirmation: the Cargo log, `uld_bookings` and `rejected_messages` show the outcome.
- 400: empty array, unreadable JSON, or a missing or invalid date. Nothing is published.
- 503: RabbitMQ unavailable. Earlier records may already be published (see the simulator log); resending the whole request is safe because Cargo ignores a ULD code already booked on that flight.

The simulator does not check weights, codes or flights; Cargo's consumer does. It has no login: it listens on loopback only and is never deployed.

## Loading window

Changing, checking and finalizing a load needs a planned flight, an unfinalized cargo plan and an open loading window; a refused request answers 409. The window opens `cargo.loading-window.opens-before-std` before STD (default `12h`) and closes `cargo.loading-window.closes-before-etd` before ETD (default `20m`), both in `cargo-service`'s `application.properties`. It is open from the opening instant and closed from the closing instant (team reading of Q06). Cargo computes it on every request from Flight's current data, so a departure delay moves only the end; it never reopens a finalized plan. Cargo reads `estimatedDeparture` when Flight supplies it. For temporary compatibility with Flight before #15 is integrated, a missing or null `estimatedDeparture` falls back to STD. Remove this fallback once the new Flight response contract is required everywhere.

- `GET .../loading-window` returns `opensAt`, `closesAt` and `checkedAt` (UTC), `flightStatus`, `finalized`, `loadingAllowed` and `refusal`: `PLAN_FINALIZED`, `FLIGHT_NOT_PLANNED`, `WINDOW_NOT_OPEN_YET`, `WINDOW_CLOSED` or `null`. It answers 200 for every known flight, also after departure; 404, 400 and 503 as for the cargo plan. A 409 carries the same `refusal`, `opensAt` and `closesAt` in its ProblemDetail.
- Change the offsets without a new build (team reading of Q21): set `CARGO_LOADINGWINDOW_OPENSBEFORESTD` and/or `CARGO_LOADINGWINDOW_CLOSESBEFOREETD` (for example `48h`, `30m`) and restart Cargo. With Compose: `CARGO_LOADINGWINDOW_OPENSBEFORESTD=48h docker compose up -d cargo-service`; Compose passes these two variables only when they are set in the shell, otherwise the defaults apply. In the IDE, set them in the run configuration.
- The startup log shows the active values (`Loading window opens PT12H before STD and closes PT20M before ETD`). A missing or negative offset, or an opening offset that is not larger than the closing offset, stops the startup with that reason.

## Loading positions

The main deck of the Boeing 747-400 freighter has 34 positions, nose to tail: `A1`, `A2`, `B1`, then left/right pairs `CL`/`CR` to `UL`/`UR` for rows C D E F G H J K L M P R S T U (no I, N, O, Q), and `X`. `RL` is left on row R, next to the door; `LR` is right on row L.

- `GET /api/loading-positions` lists them in that order as `{"code":"RL","row":"R","side":"LEFT"}` (`side` is `LEFT`, `RIGHT` or `NONE` for `A1`, `A2`, `B1` and `X`).
- `PUT /api/cargo-plans/{flightNumber}/{scheduledDepartureDate}/{departureAirportCode}/bookings/{uldCode}/position` with `{"position":"RL"}` puts a booked ULD on a position or moves it there; its old position becomes free. `DELETE` on the same path takes it off again. Both answer 204 and repeating them changes nothing. Swapping two ULDs takes two steps: clear one, then assign.
- One ULD per position and one position per ULD. Changes need a planned flight, an open loading window and an unfinalized plan ([Loading window](#loading-window)).
- 400: unknown or missing position, invalid flight key or ULD code. 404: unknown flight, or the ULD is not booked on it. 409: position taken by another ULD, loading not allowed (with `refusal`), or another change to the same cargo plan committed first ("Reload and try again"). 503: Flight is unreachable.
- Concurrency: every change to a cargo plan raises `cargo_plans.version` and Hibernate checks it at commit (optimistic locking), so the second of two overlapping changes is rolled back with 409. A unique constraint on (flight, `loading_position`) also stops two ULDs on one position in the database.
- Both planner roles can read Cargo data. Only `load-planner` can change positions: a `PUT` or `DELETE` without a valid token gets 401, while a flight planner gets 403. Loading conditions are still checked for an authorized load planner. CSRF protection is off because the stateless API accepts bearer tokens.
- A ULD code containing `/` cannot be addressed in the URL path (known limitation, Q15).
- The cargo page shows a dropdown per booking while loading is allowed and the plain position otherwise.

## Authentication

Configure the realm, roles, users, clients and audience mappers through the
Keycloak admin console. Configuration persists in MySQL across container recreation.

- Realm `air-freight`; planner users and realm roles `flight-planner` and
  `load-planner` (development user password `password`).
- Confidential backend clients `flight-service` and `cargo-service`. Cargo
  reads Flight using client credentials and `CARGO_SERVICE_CLIENT_SECRET`.
- Public frontend client `air-freight-frontend` uses authorization code with
  PKCE through `keycloak-js`. Keycloak provides the login page; tokens stay in
  memory and are refreshed before API requests. Sign-out ends the Keycloak session.
- Flight and Cargo validate the token issuer and their own audience and map
  realm roles to Spring authorities. Both planners can read APIs; Cargo changes
  require `load-planner` and Flight changes require `flight-planner`. The Cargo
  page also hides position controls for flight planners. APIs are stateless;
  page resources and health endpoints are public.
- Cargo's outgoing token manager uses a fixed `cargo-service` identity, sharing
  the cached service token across HTTP users and background message processing.
- Issuer `http://localhost:8180/realms/air-freight`. Docker services fetch signing
  keys and Cargo's service token through `http://keycloak:8080`.

Select the `dev` environment in `http/local.http` and run the token request for
the target service before its authenticated requests. Store client secrets in
the ignored `http/http-client.private.env.json`.

## Incoming message recovery

Flight and Partner use the same incoming recovery policy after the configured short retries. Each service's default listener factory uses its local recoverer component, which selects a confirmed retry publisher from an explicit source-queue map:

| Service / recoverer | Source queue | Retry queue |
| --- | --- | --- |
| Flight / `FlightMessageRecoverer` | `flight.ssm` | `flight.ssm.retry` |
| Partner / `PartnerMessageRecoverer` | `partner.flight-created` | `partner.flight-created.retry` |

## Checks

```sh
npm ci                          # Install Husky hooks; requires Node.js
./gradlew spotlessApply         # Format Java
./gradlew check assemble        # Format check, lint, tests and JARs
```

Spotless (Google Java Format/AOSP), Checkstyle, JUnit, AssertJ, ArchUnit, Testcontainers and JaCoCo. Cargo's database integration tests (`*IT`) start PostgreSQL with Testcontainers when Docker is available and are skipped without Docker, as in GitLab CI; all other tests run without Docker. Coverage reports are under `<service>/build/reports/jacoco/test/`. No coverage threshold is enforced.

Husky checks formatting and lint without modifying files. GitLab CI runs format, lint, build and test jobs per service; artifacts are retained for one week.
