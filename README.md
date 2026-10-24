# Air Freight Operations

Java 22 · Spring Boot 4.1.1 · Gradle 9.7.1 multi-project build.

| Service | HTTP port | PostgreSQL database |
| --- | --- | --- |
| `flight-service` | 8080 | `flight` |
| `cargo-service` | 8081 | `cargo` |
| `partner-communication-service` | 8082 | `partner_communication` |
| `simulator`  | 8083 | None |

The three business services use `domain`, `application`, `infrastructure` and `api` packages. Current scope: infrastructure connectivity, health checks, bearer-token authentication, Flight's US-3 SSM ingestion and a local SSM and ULD simulator (see [Local development](#local-development)). Flight exposes a flight lookup by key and a day overview (`GET /api/flights?date=`), which Cargo calls with a service token. Cargo serves the day overview to the load planner on its own `GET /api/flights?date=` and answers 503 when Flight is unreachable. Cargo books ULDs received on RabbitMQ onto planned flights; see [ULD bookings](#uld-bookings). Flight uses a plain domain model with separate JPA entities and a database adapter. Other business workflows and the web UI are not implemented.

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

## Authentication

- Realm: `air-freight`; public client: `air-freight-dev` (password grant).
- Users and corresponding realm roles: `flight-planner`, `load-planner`; password: `password`.
- Flight/Cargo `/api/status` requires a bearer token; health endpoints are public.
- Confidential client `cargo-service` (client-credentials grant, development secret `cargo-service-secret`): Cargo uses its service token to call `GET /api/flights/{flightNumber}/{scheduledDepartureDate}/{departureAirportCode}` on Flight. Compose sets Cargo's token URL to `http://keycloak:8080/...`.
- Keycloak imports the realm only when it does not exist yet. After a realm change, recreate it: `docker compose down` and `docker volume rm air-freight-operations_keycloak-data`.
- Issuer: `http://localhost:8180/realms/air-freight`. Inside Docker, signing keys are fetched from `http://keycloak:8080/realms/air-freight/protocol/openid-connect/certs`.
- Browser login and operation-specific role checks are not implemented.

Run the token request in `http/local.http` before its authenticated requests.

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

Spotless (Google Java Format/AOSP), Checkstyle, JUnit, AssertJ, ArchUnit and JaCoCo. Tests run without Docker; coverage reports are under `<service>/build/reports/jacoco/test/`. No coverage threshold is enforced.

Husky checks formatting and lint without modifying files. GitLab CI runs format, lint, build and test jobs per service; artifacts are retained for one week.
