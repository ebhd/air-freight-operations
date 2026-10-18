CREATE TABLE IF NOT EXISTS uld_bookings (
    flight_number VARCHAR(32) NOT NULL,
    scheduled_departure_date DATE NOT NULL,
    departure_airport_code CHAR(3) NOT NULL,
    uld_code VARCHAR(32) NOT NULL,
    weight_value NUMERIC NOT NULL,
    weight_unit VARCHAR(2) NOT NULL,
    destination_airport_code CHAR(3) NOT NULL,
    PRIMARY KEY (flight_number, scheduled_departure_date, departure_airport_code, uld_code)
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
