-- every change to a plan raises the version so the second of two overlapping changes fails
CREATE TABLE IF NOT EXISTS cargo_plans (
    flight_number VARCHAR(32) NOT NULL,
    scheduled_departure_date DATE NOT NULL,
    departure_airport_code CHAR(3) NOT NULL,
    version BIGINT NOT NULL,
    state VARCHAR(16) NOT NULL,
    finalized_at TIMESTAMP WITH TIME ZONE,
    -- the new flight the cargo moved to, filled when the state is REPLANNED
    moved_to_flight_number VARCHAR(32),
    moved_to_departure_date DATE,
    moved_to_departure_airport_code CHAR(3),
    PRIMARY KEY (flight_number, scheduled_departure_date, departure_airport_code),
    CONSTRAINT cargo_plans_state_check CHECK (state IN ('DRAFT', 'FINALIZED', 'REPLANNED'))
);

-- the move columns and state came later, so an existing database gets them without a reset
ALTER TABLE cargo_plans ADD COLUMN IF NOT EXISTS moved_to_flight_number VARCHAR(32);
ALTER TABLE cargo_plans ADD COLUMN IF NOT EXISTS moved_to_departure_date DATE;
ALTER TABLE cargo_plans ADD COLUMN IF NOT EXISTS moved_to_departure_airport_code CHAR(3);
ALTER TABLE cargo_plans DROP CONSTRAINT IF EXISTS cargo_plans_state_check;
ALTER TABLE cargo_plans ADD CONSTRAINT cargo_plans_state_check
    CHECK (state IN ('DRAFT', 'FINALIZED', 'REPLANNED'));

CREATE TABLE IF NOT EXISTS uld_bookings (
    flight_number VARCHAR(32) NOT NULL,
    scheduled_departure_date DATE NOT NULL,
    departure_airport_code CHAR(3) NOT NULL,
    uld_code VARCHAR(32) NOT NULL,
    weight_value NUMERIC NOT NULL,
    weight_unit VARCHAR(2) NOT NULL,
    destination_airport_code CHAR(3) NOT NULL,
    loading_position VARCHAR(2),
    PRIMARY KEY (flight_number, scheduled_departure_date, departure_airport_code, uld_code),
    -- nulls never collide so any number of bookings can stay without a position
    CONSTRAINT uld_bookings_one_uld_per_position
        UNIQUE (flight_number, scheduled_departure_date, departure_airport_code, loading_position)
);

CREATE TABLE IF NOT EXISTS processed_messages (
    message_id VARCHAR(255) PRIMARY KEY,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    flight_number VARCHAR(32) NOT NULL,
    scheduled_departure_date DATE NOT NULL,
    departure_airport_code CHAR(3) NOT NULL,
    uld_code VARCHAR(32) NOT NULL,
    outcome VARCHAR(32) NOT NULL
);

CREATE TABLE IF NOT EXISTS rejected_messages (
    id BIGSERIAL PRIMARY KEY,
    message_id TEXT,
    payload TEXT NOT NULL,
    reason TEXT NOT NULL,
    rejected_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS message_receipts (
    message_id VARCHAR(255) PRIMARY KEY,
    first_received_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- events waiting for rabbitmq, written in the same transaction as the change they report
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

-- cargos copy of the flights it needs, filled by flights events or one lookup at flight
-- last_change_at is when flight made the change so an older event never overwrites a newer one
CREATE TABLE IF NOT EXISTS flights (
    flight_number VARCHAR(32) NOT NULL,
    scheduled_departure_date DATE NOT NULL,
    departure_airport_code CHAR(3) NOT NULL,
    arrival_airport_code CHAR(3) NOT NULL,
    status VARCHAR(16) NOT NULL,
    scheduled_departure TIMESTAMP WITH TIME ZONE NOT NULL,
    scheduled_arrival TIMESTAMP WITH TIME ZONE NOT NULL,
    estimated_departure TIMESTAMP WITH TIME ZONE,
    estimated_arrival TIMESTAMP WITH TIME ZONE,
    last_change_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (flight_number, scheduled_departure_date, departure_airport_code)
);

-- the day overview searches by date which is not the first key column
CREATE INDEX IF NOT EXISTS flights_by_departure_date ON flights (scheduled_departure_date);
