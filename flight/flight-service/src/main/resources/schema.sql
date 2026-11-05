CREATE TABLE IF NOT EXISTS flights (
    flight_number VARCHAR(32) NOT NULL,
    scheduled_departure_date DATE NOT NULL,
    departure_airport_code CHAR(3) NOT NULL,
    arrival_airport_code CHAR(3) NOT NULL,
    aircraft_registration VARCHAR(32) NOT NULL,
    scheduled_departure TIMESTAMP WITH TIME ZONE NOT NULL,
    scheduled_arrival TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(16) NOT NULL,
    finalized_load_processed BOOLEAN NOT NULL DEFAULT FALSE,
    -- null until the planner registers them, an arrival needs a departure
    actual_departure TIMESTAMP WITH TIME ZONE,
    actual_arrival TIMESTAMP WITH TIME ZONE,
    -- filled when the flight is replanned: the new schedule and the delay change that caused it
    replanned_flight_number VARCHAR(32),
    replanned_departure TIMESTAMP WITH TIME ZONE,
    replanned_arrival TIMESTAMP WITH TIME ZONE,
    replan_delay_type VARCHAR(16),
    replan_minutes INTEGER,
    replan_reason_code VARCHAR(16),
    replan_description TEXT,
    replanned_at TIMESTAMP WITH TIME ZONE,
    replacement_created BOOLEAN NOT NULL DEFAULT FALSE,
    -- the key of the replanned flight this one replaces, so the history reads both ways
    replaces_flight_number VARCHAR(32),
    replaces_departure_date DATE,
    replaces_departure_airport_code CHAR(3),
    PRIMARY KEY (flight_number, scheduled_departure_date, departure_airport_code),
    CHECK (actual_arrival IS NULL OR actual_departure IS NOT NULL)
);

-- the same two columns for a database made before them, so no reset is needed
ALTER TABLE flights ADD COLUMN IF NOT EXISTS actual_departure TIMESTAMP WITH TIME ZONE;
ALTER TABLE flights ADD COLUMN IF NOT EXISTS actual_arrival TIMESTAMP WITH TIME ZONE;

-- the replanning columns came later, so an existing database gets them without a reset
ALTER TABLE flights ADD COLUMN IF NOT EXISTS replanned_flight_number VARCHAR(32);
ALTER TABLE flights ADD COLUMN IF NOT EXISTS replanned_departure TIMESTAMP WITH TIME ZONE;
ALTER TABLE flights ADD COLUMN IF NOT EXISTS replanned_arrival TIMESTAMP WITH TIME ZONE;
ALTER TABLE flights ADD COLUMN IF NOT EXISTS replan_delay_type VARCHAR(16);
ALTER TABLE flights ADD COLUMN IF NOT EXISTS replan_minutes INTEGER;
ALTER TABLE flights ADD COLUMN IF NOT EXISTS replan_reason_code VARCHAR(16);
ALTER TABLE flights ADD COLUMN IF NOT EXISTS replan_description TEXT;
ALTER TABLE flights ADD COLUMN IF NOT EXISTS replanned_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE flights ADD COLUMN IF NOT EXISTS replacement_created BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE flights ADD COLUMN IF NOT EXISTS replaces_flight_number VARCHAR(32);
ALTER TABLE flights ADD COLUMN IF NOT EXISTS replaces_departure_date DATE;
ALTER TABLE flights ADD COLUMN IF NOT EXISTS replaces_departure_airport_code CHAR(3);

CREATE TABLE IF NOT EXISTS delay_reasons (
    code VARCHAR(16) PRIMARY KEY,
    label VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS rejected_messages (
    id BIGSERIAL PRIMARY KEY,
    message_id TEXT,
    payload TEXT NOT NULL,
    reason TEXT NOT NULL,
    rejected_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS departure_delays (
    id UUID PRIMARY KEY,
    flight_number VARCHAR(32) NOT NULL,
    scheduled_departure_date DATE NOT NULL,
    departure_airport_code CHAR(3) NOT NULL,
    minutes INTEGER NOT NULL CHECK (minutes BETWEEN 1 AND 720),
    reason_code VARCHAR(16) NOT NULL,
    description TEXT NOT NULL,
    CONSTRAINT fk_departure_delays_flight
        FOREIGN KEY (flight_number, scheduled_departure_date, departure_airport_code)
        REFERENCES flights (flight_number, scheduled_departure_date, departure_airport_code),
    CONSTRAINT fk_departure_delays_reason
        FOREIGN KEY (reason_code) REFERENCES delay_reasons (code)
);

CREATE INDEX IF NOT EXISTS departure_delays_flight_idx
    ON departure_delays (flight_number, scheduled_departure_date, departure_airport_code);

CREATE TABLE IF NOT EXISTS arrival_delays (
    id UUID PRIMARY KEY,
    flight_number VARCHAR(32) NOT NULL,
    scheduled_departure_date DATE NOT NULL,
    departure_airport_code CHAR(3) NOT NULL,
    minutes INTEGER NOT NULL CHECK (minutes BETWEEN 1 AND 720),
    reason_code VARCHAR(16) NOT NULL,
    description TEXT NOT NULL,
    CONSTRAINT fk_arrival_delays_flight
        FOREIGN KEY (flight_number, scheduled_departure_date, departure_airport_code)
        REFERENCES flights (flight_number, scheduled_departure_date, departure_airport_code),
    CONSTRAINT fk_arrival_delays_reason
        FOREIGN KEY (reason_code) REFERENCES delay_reasons (code)
);

CREATE INDEX IF NOT EXISTS arrival_delays_flight_idx
    ON arrival_delays (flight_number, scheduled_departure_date, departure_airport_code);


CREATE TABLE IF NOT EXISTS outbox_messages (
    id UUID PRIMARY KEY,
    exchange_name VARCHAR(255) NOT NULL,
    routing_key VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE

);

CREATE INDEX IF NOT EXISTS outbox_messages_pending_idx
    ON outbox_messages (created_at, id)
    WHERE published_at IS NULL;

-- cpm messages from cargo that were applied, so a redelivery has no second effect
CREATE TABLE IF NOT EXISTS processed_messages (
    message_id UUID PRIMARY KEY,
    flight_number VARCHAR(32) NOT NULL,
    scheduled_departure_date DATE NOT NULL,
    departure_airport_code CHAR(3) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
