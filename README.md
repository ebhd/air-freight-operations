# Air Freight Operations

Java 22 · Spring Boot 4.1.1 · Gradle 9.7.1 multi-project build.

| Service | HTTP port | PostgreSQL database |
| --- | --- | --- |
| `flight-service` | 8080 | `flight` |
| `cargo-service` | 8081 | `cargo` |
| `partner-communication-service` | 8082 | `partner_communication` |
| `simulator`  | 8083 | None |

The three business services use `domain`, `application`, `infrastructure` and `api` packages. Current scope: infrastructure connectivity, health checks, bearer-token authentication, Flight's US-3 SSM ingestion and a local SSM simulator (see [Local development](#local-development)). Flight exposes a flight lookup by key and a day overview (`GET /api/flights?date=`), which Cargo calls with a service token. Cargo serves the day overview to the load planner on its own `GET /api/flights?date=`, which hides departed and landed flights unless `includeDeparted=true`, and answers 503 when Flight is unreachable. `GET /api/cargo-plans/{flightNumber}/{scheduledDepartureDate}/{departureAirportCode}` returns one flight's data and status from Flight with all its ULD bookings (`position` stays `null` until positions exist), also after departure: 404 for a flight Flight does not know, 400 for an invalid key, 503 when Flight is unreachable. `GET /api/cargo-plans/{flightNumber}/{scheduledDepartureDate}/{departureAirportCode}/loading-window` returns the flight's loading window and whether loading is allowed now; see [Loading window](#loading-window). Cargo also serves the load planner's "Flights to load" page at `http://localhost:8081/`, which links to each flight's "Cargo plan" page (`cargo-plan.html`); both pages are written in TypeScript with Tailwind CSS and compiled by the Gradle build (see [Cargo frontend](#cargo-frontend)). Cargo books ULDs received on RabbitMQ onto planned flights; see [ULD bookings](#uld-bookings). Flight uses a plain domain model with separate JPA entities and a database adapter. Other business workflows and Flight's web UI are not implemented.

## Run

Requires Docker with Compose:

```sh
docker compose up -d --build --wait
docker compose ps
docker compose down
```

`--wait` waits for healthy containers. `/actuator/health` on each service checks PostgreSQL and RabbitMQ; Cargo also checks Flight over HTTP. Dependencies are checked at startup, without automatic restart after later outages.

Named volumes preserve data after shutdown. PostgreSQL initializes the three databases only on the first start with an empty volume.

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

Development only: demo credentials and Keycloak `start-dev` with persistent file storage.

Configuration lives in each service's `application.properties`; standard Spring environment variables override it. Compose sets container hostnames and Cargo's `FLIGHT_API_URL`.

### Cargo frontend

Page HTML lives in `cargo-service/src/main/resources/static/`, TypeScript modules in `cargo-service/src/main/typescript/` and the Tailwind CSS input in `cargo-service/src/main/css/app.css`. Gradle's `processResources` (part of `bootJar`, `bootRun` and `check`) runs `tsc` and the Tailwind CLI and puts the output in the JAR as `static/js/*.js` and `static/css/app.css`. The Gradle Node plugin downloads the pinned Node version and runs `npm ci` in `cargo-service/`, so no Node installation is needed, also not in Docker or CI. A TypeScript type error fails the build. Compiled files are not committed; edit the sources.

For manual SSM submission, start Flight Service and run `./gradlew :simulator:bootRun`. Then send the request in [simulator/http/ssm.http](simulator/http/ssm.http). The simulator runs locally on port 8083; it is not part of the default Compose deployment.

## ULD bookings

Cargo consumes ULD bookings from the durable `cargo.uld` queue. The official ULD JSON schema has not been published (Q01), so this is the team's provisional contract:

```json
{
  "messageId": "uld-0001",
  "flightNumber": "HJ221",
  "scheduledDepartureDay": 19,
  "departureAirportCode": "BRU",
  "uldCode": "AKE12345KL",
  "weight": { "value": 1250.5, "unit": "KG" },
  "destination": "AMS"
}
```

- The AMQP `message_id` property identifies the message; `messageId` in the body is used when it is absent.
- The day of month is resolved to the nearest date relative to when the message ID was first received (`message_receipts`), preferring the future on a tie (team interpretation of Q02). Retries and requeues reuse that instant. Weight units are `KG` or `LB` (Q15).
- The flight must exist in Flight and be planned. A ULD code already booked on that flight is ignored and logged, even when weight or destination differ; the existing booking never changes. Differing content is logged as a warning.
- Two deliveries processed at the same time cannot book the same ULD twice: the database rejects the second insert, and its retry records it as a duplicate.
- A redelivered message ID has no second effect: `processed_messages` keeps the first result.
- Unreadable or invalid messages, unknown flights and flights that are no longer planned are stored in `rejected_messages` with their content and reason; a redelivered rejected message ID stays rejected. Database or Flight outages are retried and the message is requeued, not dropped.

Until the ULD simulator exists, publish a booking manually: create the flight with the SSM simulator, then publish the JSON above to `cargo.uld` in the RabbitMQ management UI with a `message_id` property.

## Loading window

Changing, checking and finalizing a load needs a planned flight, an unfinalized cargo plan and an open loading window; a refused request answers 409. The window opens `cargo.loading-window.opens-before-std` before STD (default `12h`) and closes `cargo.loading-window.closes-before-etd` before ETD (default `20m`), both in `cargo-service`'s `application.properties`. It is open from the opening instant and closed from the closing instant (team reading of Q06). Cargo computes it on every request from Flight's current data, so a departure delay moves only the end; it never reopens a finalized plan. Cargo reads `estimatedDeparture` when Flight supplies it. For temporary compatibility with Flight before #15 is integrated, a missing or null `estimatedDeparture` falls back to STD. Remove this fallback once the new Flight response contract is required everywhere.

- `GET .../loading-window` returns `opensAt`, `closesAt` and `checkedAt` (UTC), `flightStatus`, `finalized`, `loadingAllowed` and `refusal`: `PLAN_FINALIZED`, `FLIGHT_NOT_PLANNED`, `WINDOW_NOT_OPEN_YET`, `WINDOW_CLOSED` or `null`. It answers 200 for every known flight, also after departure; 404, 400 and 503 as for the cargo plan. A 409 carries the same `refusal`, `opensAt` and `closesAt` in its ProblemDetail.
- Change the offsets without a new build (team reading of Q21): set `CARGO_LOADINGWINDOW_OPENSBEFORESTD` and/or `CARGO_LOADINGWINDOW_CLOSESBEFOREETD` (for example `48h`, `30m`) and restart Cargo. With Compose: `CARGO_LOADINGWINDOW_OPENSBEFORESTD=48h docker compose up -d cargo-service`; Compose passes these two variables only when they are set in the shell, otherwise the defaults apply. In the IDE, set them in the run configuration.
- The startup log shows the active values (`Loading window opens PT12H before STD and closes PT20M before ETD`). A missing or negative offset, or an opening offset that is not larger than the closing offset, stops the startup with that reason.

## Authentication

- Realm: `air-freight`; public client: `air-freight-dev` (password grant, web origin `http://localhost:8081`). Access tokens last 30 minutes in this development realm.
- Users and corresponding realm roles: `flight-planner`, `load-planner`; password: `password`.
- Flight/Cargo `/api/status` requires a bearer token; health endpoints are public.
- Confidential client `cargo-service` (client-credentials grant, development secret `cargo-service-secret`): Cargo uses its service token to call `GET /api/flights/{flightNumber}/{scheduledDepartureDate}/{departureAirportCode}` on Flight. Compose sets Cargo's token URL to `http://keycloak:8080/...`.
- Keycloak imports the realm only when it does not exist yet. After a realm change, recreate it: `docker compose down` and `docker volume rm air-freight-operations_keycloak-data`. The web origin and token lifespan for the Cargo page (#12) are such a change.
- Issuer: `http://localhost:8180/realms/air-freight`. Inside Docker, signing keys are fetched from `http://keycloak:8080/realms/air-freight/protocol/openid-connect/certs`.
- Cargo's page uses a development sign-in until browser login (#1) exists: it sends username and password to Keycloak's token endpoint (password grant) and keeps the access token in the tab's `sessionStorage`. Page files (`/`, `*.html`, `/css/**`, `/js/**`) load without a token; every `/api` request needs one. #1 replaces this with Keycloak's login page.
- Operation-specific role checks are not implemented.

Run the token request in `http/local.http` before its authenticated requests.

## Checks

```sh
npm ci                          # Install Husky hooks; requires Node.js
./gradlew spotlessApply         # Format Java
./gradlew check assemble        # Format check, lint, tests and JARs
```

Spotless (Google Java Format/AOSP), Checkstyle, JUnit, AssertJ, ArchUnit, Testcontainers and JaCoCo. Cargo's database integration tests (`*IT`) start PostgreSQL with Testcontainers when Docker is available and are skipped without Docker, as in GitLab CI; all other tests run without Docker. Coverage reports are under `<service>/build/reports/jacoco/test/`. No coverage threshold is enforced.

Husky checks formatting and lint without modifying files. GitLab CI runs format, lint, build and test jobs per service; artifacts are retained for one week.
