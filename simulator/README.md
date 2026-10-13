# Simulator

Small standalone Spring Boot application for submitting SSM schedules through RabbitMQ. It has no database or dependency on Flight Service's Java code.

## Run locally

Start RabbitMQ and Flight Service first, so Flight declares `flight.ssm` and its dead-letter topology. From the repository root:

```sh
./gradlew :simulator:bootRun
```

The simulator listens on `127.0.0.1:8083`. Open `http/ssm.http` in your IDE and run its request. Connection settings are in `src/main/resources/application.properties` and support standard Spring environment overrides.

The endpoint is available whenever the simulator application is running. The local endpoint has no login requirement and binds only to loopback by default. Do not deploy this application in production; it is not included in the normal Docker Compose deployment.

## HTTP contract

`POST /api/simulator/flight-schedules` accepts a nonempty JSON array of schedules, including an array with just one entry. Each entry contains start/end dates, operating weekdays, UTC departure/arrival times, flight number, departure/arrival airports and registration. See `http/ssm.http` for a complete example.

Each record gets a generated message ID and becomes one SSM matching the existing Flight Service JSON contract. The publisher uses RabbitMQ's default exchange and the `flight.ssm` routing key. Messages use persistent delivery and JSON content type. Flight Service performs schedule expansion and business validation through its existing listener.

`202 Accepted` returns `{"messageIds":["..."]}` after RabbitMQ acknowledges and routes every message. This confirms submission only: flight creation happens asynchronously and may fail validation. Inspect Flight Service and its DLQ for processing outcomes.

Malformed JSON may be rejected by Spring. Schedule business rules are checked by Flight Service.

## Structure

- `ssm`: controller, request record, message record and publisher.
- `configuration`: shared JSON converter configuration.

The controller delegates directly to the publisher. Message-specific code stays in its own package; common messaging configuration stays outside it.

No automated simulator tests have been added.

Compilation, formatting and Checkstyle passed.
