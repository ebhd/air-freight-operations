CREATE TABLE IF NOT EXISTS flights (
    flight_number VARCHAR(32) NOT NULL,
    scheduled_departure_date DATE NOT NULL,
    departure_airport_code CHAR(3) NOT NULL,
    arrival_airport_code CHAR(3) NOT NULL,
    aircraft_registration VARCHAR(32) NOT NULL,
    scheduled_departure TIMESTAMP WITH TIME ZONE NOT NULL,
    scheduled_arrival TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(16) NOT NULL,
    PRIMARY KEY (flight_number, scheduled_departure_date, departure_airport_code)
);

CREATE TABLE IF NOT EXISTS rejected_messages (
    id BIGSERIAL PRIMARY KEY,
    message_id VARCHAR(128) NOT NULL,
    content TEXT NOT NULL,
    reason TEXT NOT NULL,
    processing_stage VARCHAR(64) NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL
);
