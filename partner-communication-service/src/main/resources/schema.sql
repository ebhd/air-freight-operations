CREATE TABLE IF NOT EXISTS partner_messages (
    id UUID PRIMARY KEY,
    source_message_id UUID NOT NULL,
    created_at_utc TIMESTAMP WITH TIME ZONE NOT NULL,
    type VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    format_version INTEGER NOT NULL CHECK (format_version > 0),
    state VARCHAR(255) NOT NULL CHECK (state IN ('PENDING', 'PUBLISHED')),
    CONSTRAINT partner_messages_source_type_unique UNIQUE (source_message_id, type)
);

CREATE INDEX IF NOT EXISTS partner_messages_pending_idx
    ON partner_messages (created_at_utc, id) WHERE state = 'PENDING';
