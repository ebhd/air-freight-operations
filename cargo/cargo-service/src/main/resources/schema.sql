-- every change to a plan raises the version so the second of two overlapping changes fails
CREATE TABLE IF NOT EXISTS cargo_plans (
    flight_number VARCHAR(32) NOT NULL,
    scheduled_departure_date DATE NOT NULL,
    departure_airport_code CHAR(3) NOT NULL,
    version BIGINT NOT NULL,
    PRIMARY KEY (flight_number, scheduled_departure_date, departure_airport_code)
);

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
