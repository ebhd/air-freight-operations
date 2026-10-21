# Air Freight Operations

Java 22 · Spring Boot 4.1.1 · Gradle 9.7.1 multi-project build.

| Service | HTTP port | PostgreSQL database |
| --- | --- | --- |
| `flight-service` | 8080 | `flight` |
| `cargo-service` | 8081 | `cargo` |
| `partner-communication-service` | 8082 | `partner_communication` |
| `simulator`  | 8083 | None |

The three business services use `domain`, `application`, `infrastructure` and `api` packages. Current scope: infrastructure connectivity, health checks, bearer-token authentication, Flight's US-3 SSM ingestion and a local SSM simulator (see [Local development](#local-development)). Flight uses a plain domain model with separate JPA entities and a database adapter. Other business workflows and the web UI are not implemented.

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

## Authentication

- Realm: `air-freight`; public client: `air-freight-dev` (password grant).
- Users and corresponding realm roles: `flight-planner`, `load-planner`; password: `password`.
- Flight/Cargo `/api/status` requires a bearer token; health endpoints are public.
- Issuer: `http://localhost:8180/realms/air-freight`. Inside Docker, signing keys are fetched from `http://keycloak:8080/realms/air-freight/protocol/openid-connect/certs`.
- Browser login and operation-specific role checks are not implemented.

Run the token request in `http/local.http` before its authenticated requests.

## Incoming message recovery

Flight and Partner use the same incoming recovery policy after the configured short retries. Each service's default listener factory uses its local recoverer component, which selects a confirmed retry publisher from an explicit source-queue map:

| Service / recoverer | Source queue | Retry queue |
| --- | --- | --- |
| Flight / `FlightMessageRecoverer` | `flight.ssm` | `flight.ssm.retry` |
| Partner / `PartnerMessageRecoverer` | `partner.flight-created` | `partner.flight-created.retry` |

Temporary database failures are deferred through durable quorum retry queues with a 30-second message TTL and at-least-once dead-lettering back to the source queue. Other processing failures use the source queue's DLQ configuration. Missing retry mappings or failed retry publication requeue the original delivery. Add mappings when implementing new listeners and their retry queues; Partner's commented CPM entry is an example only. Short retry settings remain in each service's `spring.rabbitmq.listener.simple.retry.*`. The existing source queues and DLQs are unchanged; the new SSM retry queue does not automatically replay SSMs already in `flight.dlq`.

## Checks

```sh
npm ci                          # Install Husky hooks; requires Node.js
./gradlew spotlessApply         # Format Java
./gradlew check assemble        # Format check, lint, tests and JARs
```

Spotless (Google Java Format/AOSP), Checkstyle, JUnit, AssertJ, ArchUnit and JaCoCo. Tests run without Docker; coverage reports are under `<service>/build/reports/jacoco/test/`. No coverage threshold is enforced.

US-5 integration tests use `@SpringBootTest`, an in-memory H2 database and a mocked RabbitMQ sender. Flight tests cover SSM processing, duplicate messages and overlapping schedules; Partner tests cover FLM contents and duplicate handling. They run with the normal `test` task locally and in CI. These are separate service tests, not a broker-connected end-to-end test: RabbitMQ delivery/retry behavior and the PostgreSQL schema still need verification against the running stack.

Husky checks formatting and lint without modifying files. GitLab CI runs format, lint, build and test jobs per service; artifacts are retained for one week.
