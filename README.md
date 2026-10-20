# Air Freight Operations

Java 22 · Spring Boot 4.1.1 · Gradle 9.7.1 multi-project build.

| Service | HTTP port | PostgreSQL database |
| --- | --- | --- |
| `flight-service` | 8080 | `flight` |
| `cargo-service` | 8081 | `cargo` |
| `partner-communication-service` | 8082 | `partner_communication` |
| `simulator`  | 8083 | None |

The three business services use `domain`, `application`, `infrastructure` and `api` packages. Current scope: infrastructure connectivity, health checks, bearer-token authentication, Flight's US-3 SSM ingestion and a local SSM simulator (see [Local development](#local-development)). Flight exposes a flight lookup by key and a day overview (`GET /api/flights?date=`), which Cargo calls with a service token. Cargo serves the day overview to the load planner on its own `GET /api/flights?date=` and answers 503 when Flight is unreachable. Cargo books ULDs received on RabbitMQ onto planned flights; see [ULD bookings](#uld-bookings). Flight uses a plain domain model with separate JPA entities and a database adapter. Other business workflows and the web UI are not implemented.

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

## Authentication

- Realm: `air-freight`; public client: `air-freight-dev` (password grant).
- Users and corresponding realm roles: `flight-planner`, `load-planner`; password: `password`.
- Flight/Cargo `/api/status` requires a bearer token; health endpoints are public.
- Confidential client `cargo-service` (client-credentials grant, development secret `cargo-service-secret`): Cargo uses its service token to call `GET /api/flights/{flightNumber}/{scheduledDepartureDate}/{departureAirportCode}` on Flight. Compose sets Cargo's token URL to `http://keycloak:8080/...`.
- Keycloak imports the realm only when it does not exist yet. After a realm change, recreate it: `docker compose down` and `docker volume rm air-freight-operations_keycloak-data`.
- Issuer: `http://localhost:8180/realms/air-freight`. Inside Docker, signing keys are fetched from `http://keycloak:8080/realms/air-freight/protocol/openid-connect/certs`.
- Browser login and operation-specific role checks are not implemented.

Run the token request in `http/local.http` before its authenticated requests.

## Checks

```sh
npm ci                          # Install Husky hooks; requires Node.js
./gradlew spotlessApply         # Format Java
./gradlew check assemble        # Format check, lint, tests and JARs
```

Spotless (Google Java Format/AOSP), Checkstyle, JUnit, AssertJ, ArchUnit and JaCoCo. Tests run without Docker; coverage reports are under `<service>/build/reports/jacoco/test/`. No coverage threshold is enforced.

Husky checks formatting and lint without modifying files. GitLab CI runs format, lint, build and test jobs per service; artifacts are retained for one week.
