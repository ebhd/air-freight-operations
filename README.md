# Air Freight Operations

Stack: Java 22, Spring Boot 4.1.1, Gradle 9.7.1 (multi-project build), PostgreSQL, RabbitMQ, Keycloak.

| Project | Port | Database | Purpose |
| --- | --- | --- | --- |
| `flight/flight-service` and `flight/flight-frontend` | 8080 | `flight` | Flights from SSM, delays, flight planner pages |
| `cargo/cargo-service` and `cargo/cargo-frontend` | 8081 | `cargo` | ULD bookings, loading window, positions, load planner pages |
| `partner-communication-service` | 8082 | `partner_communication` | FLM and MVT ED/EA messages to partners |
| `simulator` | 8083 | none | Sends test SSM and ULD messages, local only |

Each service uses the packages `domain`, `application`, `infrastructure` and `api`. The design is in [architecture/analyse.md](architecture/analyse.md).

## Run

Requires Docker with Compose. Set up Keycloak first (see [Keycloak](#keycloak)), then:

```sh
docker compose up -d --build --wait
docker compose down
```

Pages: Flight at `http://localhost:8080/`, Cargo at `http://localhost:8081/`. Each service's `/actuator/health` checks its database and RabbitMQ; Cargo's also checks Flight.

Each service creates its tables from `schema.sql` and Hibernate validates them. There is no migration tool, so after a schema change you recreate that service's database. Example for Cargo:

```sh
docker compose stop cargo-service
docker compose exec app-postgres psql -U user -d postgres -c 'DROP DATABASE cargo WITH (FORCE)' -c 'CREATE DATABASE cargo'
docker compose up -d --build --wait cargo-service
```

`docker compose down -v` resets everything, including the Keycloak realm.

## Local development

Import the root Gradle project with JDK 22. Start the infrastructure, then run a service from the IDE or with `./gradlew :<project>:bootRun`, for example `./gradlew :flight:flight-service:bootRun`.

```sh
docker compose up -d --wait app-postgres app-rabbitmq keycloak
```

| Infrastructure | Address | User | Password |
| --- | --- | --- | --- |
| PostgreSQL | `localhost:5443` | `user` | `password` |
| RabbitMQ | `localhost:5672`, management `http://localhost:15672` | `user` | `password` |
| Keycloak (dev mode) | `http://localhost:8180` | `admin` | `admin` |
| Keycloak database (MySQL) | `localhost:3307` | `keycloak` | `password` |

These credentials are for development only.

Settings are in each service's `application.properties` and can be overridden with environment variables. Compose reads `.env`; the IDE does not, so set `CARGO_SERVICE_CLIENT_SECRET` in Cargo's run configuration.

The loading window opens 12h before STD and closes 20m before ETD. Change this with `CARGO_LOADINGWINDOW_OPENSBEFORESTD` and `CARGO_LOADINGWINDOW_CLOSESBEFOREETD` (for example `48h`, `30m`) and restart Cargo.

### Frontends

The pages are TypeScript with Tailwind CSS and `keycloak-js`, built with Vite in `cargo/cargo-frontend` and `flight/flight-frontend`. Building a service also builds its pages into the JAR. Gradle downloads Node, so you do not need to install it.

The Keycloak settings `VITE_KEYCLOAK_URL`, `VITE_KEYCLOAK_REALM` and `VITE_KEYCLOAK_CLIENT_ID` are fixed at build time. Rebuild after changing them.

For live reload, keep the backend running and start one of:

```sh
./gradlew :flight:flight-frontend:npm_run_dev   # http://localhost:5173
./gradlew :cargo:cargo-frontend:npm_run_dev     # http://localhost:5174
```

Vite forwards `/api` to the backend. Add the dev URL to the frontend client's redirect URIs in Keycloak first (for Cargo also to its web origins).

### Simulator

Run `./gradlew :simulator:bootRun`. It listens on 127.0.0.1:8083 and is never deployed.

1. Send [simulator/http/ssm.http](simulator/http/ssm.http) and wait until the flight exists.
2. Send [simulator/http/uld.http](simulator/http/uld.http). Use a departure date within two weeks, because a ULD message only carries the day of the month.

A 202 answer only means the message was published. The Cargo log and database show the result.

## Keycloak

1. Import [infrastructure/keycloak/air-freight-realm.json](infrastructure/keycloak/air-freight-realm.json) in the admin console: Manage realms, Create realm, Browse. It contains the realm `air-freight`, the roles `flight-planner` and `load-planner`, the clients and the audience mappers. It has no users and its secrets are masked.
2. Create one flight planner and one load planner user and give each the matching realm role.
3. Under Clients, Credentials, regenerate the secrets of `flight-service` and `cargo-service`. Put Cargo's secret in `.env` as `CARGO_SERVICE_CLIENT_SECRET`, put both in `http/http-client.private.env.json` (copy it from the `.example` file), then restart Cargo.

The pages sign in with the public clients `flight-frontend` and `cargo-frontend` (authorization code with PKCE). Both planners can read everything. Only a flight planner can change Flight data and only a load planner can change Cargo data. Cargo reads Flight with its own service token. Cargo's pages, health endpoints and `/error` need no token; every other path without its own rule is refused (401 without a token, 403 with one).

## API

Every API request needs a bearer token. Times are UTC. In the paths below, `{flight}` stands for `{flightNumber}/{scheduledDepartureDate}/{departureAirportCode}`, for example `HJ221/2026-10-19/BRU`. Example requests are in [http/local.http](http/local.http).

| Service | Method | Path | Role needed |
| --- | --- | --- | --- |
| Flight | GET | `/api/flights?date=2026-10-19` | any planner |
| Flight | GET | `/api/flights/{flight}` | any planner |
| Flight | GET | `/api/delay-reasons` | any planner |
| Flight | POST | `/api/flights/{flight}/delays` | flight planner |
| Flight | PATCH, DELETE | `/api/flights/{flight}/delays/{delayId}` | flight planner |
| Cargo | GET | `/api/flights?date=2026-10-19`, add `&includeDeparted=true` for departed flights | any planner |
| Cargo | GET | `/api/cargo-plans/{flight}` | any planner |
| Cargo | GET | `/api/cargo-plans/{flight}/loading-window` | any planner |
| Cargo | GET | `/api/loading-positions` | any planner |
| Cargo | PUT, DELETE | `/api/cargo-plans/{flight}/bookings/{uldCode}/position` | load planner |

Errors use `ProblemDetail`: 400 for invalid input, 404 for an unknown flight, 409 when a business rule refuses the action (with the reason), 503 when Cargo cannot reach Flight.

## Messages

| Queue or exchange | Routing keys | Content |
| --- | --- | --- |
| `flight.ssm` | | SSM from the simulator |
| `cargo.uld` | | ULD bookings, provisional format below (Q01) |
| `flight.events` | `flight.created`, `flight.estimates-changed` | From Flight to Partner, sent through Flight's outbox |
| `partner.messages` | `flm`, `mvt.ed`, `mvt.ea` | To partners: FLM as JSON, MVT as three lines of text with UTC times |

ULD booking:

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

Every message has a stable ID and is processed only once. Cargo stores invalid ULD messages in its `rejected_messages` table. Flight and Partner send invalid messages to a dead-letter queue (`flight.dlq`, `partner.dlq`) and requeue temporary database failures on the original queue after the configured short retries.

## Checks

```sh
npm ci                     # installs the Husky git hooks, needs Node.js
./gradlew spotlessApply    # formats the Java code
./gradlew check assemble   # format check, lint, tests and JARs
```

Tools: Spotless, Checkstyle, JUnit, ArchUnit, Testcontainers, JaCoCo and Vitest (Flight and Cargo pages). Coverage reports are in `<project>/build/reports/jacoco/test/`. Database tests (`*IT`) need Docker and are skipped without it, as in GitLab CI.
