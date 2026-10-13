# Flight service

## Current tests

At the user's request (2026-10-14), Flight Service keeps six unit tests:

- Expand selected weekdays within an inclusive date range.
- Use the next arrival date when arrival time is earlier than departure.
- Use the next arrival date when arrival time equals departure.
- Create flights with `PLANNED` status and the correct UTC flight key.
- Skip existing flights while continuing the remaining dates.
- Create no additional flights when the same SSM is processed again.

These are in `FlightScheduleTest` (four tests) and `FlightSchedulingServiceTest` (two tests). Validation, converter, listener, status-security and architecture tests were removed from this service. Automated RabbitMQ/PostgreSQL integration coverage remains pending; these six tests do not establish the project's full testing requirements.

## Implementation structure

`FlightSchedulingService` uses Lombok's `@Slf4j` to generate its `log` field, matching the teacher's logging style. Gradle configures Lombok as a compile-only dependency and annotation processor; Spring Boot manages its version.

The service follows the teacher's [full-DDD persistence example](https://gitlab.com/kdg-ti/software-architecture/workshops/2026-27/ordering-system/-/tree/3-jpa-full-ddd), adapted to Air Freight:

- `domain`: plain Flight aggregate, immutable flight key/schedule and repository interface; flight equality uses its business key.
- `application`: `FlightSchedulingService.createFlights` owns the scheduling use case and transaction boundary. It accepts a message ID and domain `FlightSchedule`, and returns a processing result.
- `infrastructure.persistence`: `DbFlightRepository` maps the domain through `JpaFlight.fromDomain`/`toDomain`.
- `infrastructure.persistence.jpa`: separate JPA entities and Spring Data repository. `JpaFlightId` maps the existing composite flight key.
- `infrastructure.messaging`: RabbitMQ configuration, JSON converter, incoming SSM message record and typed listener. Spring converts JSON into `SsmMessage`; the listener constructs a validated `FlightSchedule` and calls `flightSchedulingService.createFlights(message.messageId(), schedule)`. Spring's listener container handles failures and retries, including schedule validation failures before the service is invoked.

`schema.sql` initializes missing tables. Hibernate validates the mappings against that schema (`ddl-auto=validate`); Open EntityManager in View is disabled. Schema changes to existing databases need an explicit migration.

Persistence follows the teacher's check-then-save approach. The application service looks up each flight key, logs and skips existing flights, and otherwise calls `save`. `DbFlightRepository` maps to `JpaFlight`, delegates to Spring Data's inherited `save`, and maps the result back. `JpaFlightRepository` needs no custom SQL. The lookup and save are separate operations: this approach assumes scheduling is processed sequentially and does not guarantee unchanged schedules with concurrent writers. JPA save can update an existing row; revisit concurrency handling before adding parallel scheduling writers. Database failures propagate through the configured retry/DLQ handling.

## SSM input contract for US-3

The Flight service consumes project-owned JSON messages from the durable RabbitMQ queue named flight.ssm. The field names are an implementation contract chosen for this project; the requirements define the meaning and required information, not an external JSON schema.

Following the teacher's style, the queue declaration and listener both use `RabbitConfiguration.SSM_QUEUE = "flight.ssm"` directly. The queue name is no longer a separate application property. `FLIGHT_EVENTS = "flight.events"` reserves the event-exchange name; that exchange is not yet declared or used for publishing.

`RabbitConfiguration` follows the teacher's dead-letter topology: `flight.ssm` names `flight.dlx` as its dead-letter exchange, and the durable `flight.dlq` queue is bound to that fanout exchange. Spring uses three retries with exponential backoff starting at one second (multiplier two), then rejects without requeue. Failed messages, including malformed JSON and invalid schedules, are retained in `flight.dlq` with their original payload and RabbitMQ dead-letter metadata. Framework logs record the failure cause. There is no automatic DLQ consumer: inspection and controlled redelivery after correction/recovery remain operational work.

Publish JSON with AMQP `content_type=application/json` and persistent delivery mode. No Java type headers are required: Spring infers `SsmMessage` from the listener argument. Clients that previously labelled JSON as `text/plain` must use the JSON content type.

The database rejection-writing code has been removed. The legacy `rejected_messages` table definition is retained for compatibility and historical records; this SSM flow no longer writes to it. Message-ID tracking and the `processed_ssm_messages` table definition have been removed. Existing databases may still contain that unused table; startup does not drop it or its historical records.

An existing `flight.ssm` queue declared without a dead-letter argument cannot be redeclared with different arguments. For an existing broker, arrange a migration that preserves pending messages before recreating the input queue; do not delete the RabbitMQ data volume. A fresh broker receives the topology automatically at application startup.

Example message:

```json
{
  "messageId": "ssm-2026-10-12-001",
  "startDate": "2026-10-12",
  "endDate": "2026-10-18",
  "operatingDays": ["MONDAY", "WEDNESDAY"],
  "departureTime": "08:00:00",
  "arrivalTime": "10:00:00",
  "flightNumber": "HJ221",
  "departureAirportCode": "BRU",
  "arrivalAirportCode": "AMS",
  "aircraftRegistration": "SXBAA"
}
```

Dates and times in an SSM message are UTC. The date range is inclusive. The consumer creates one planned flight for each selected weekday. If arrival time is earlier than or equal to the departure time, the scheduled arrival is on the following UTC date. A flight is identified by flight number, scheduled UTC departure date and departure airport.

The consumer logs and skips existing flight keys and continues with other dates, including when an SSM is redelivered. Existing flights remain unchanged. Message IDs are kept for log correlation and processing results, not tracked in a separate store. Failed messages are retained in the DLQ and logged. US-3 creates the planned flight; US-5 owns the FLM publication handoff. Handling redelivery after flight retention cleanup remains unresolved (Q12).
