-- Dead-Letter Queue (DLQ) Messages Table
-- Stores failed order messages from Kafka for administrative review and replay

CREATE TABLE IF NOT EXISTS dlq_messages (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    original_order_id   UUID,
    original_message    TEXT                NOT NULL,
    failure_reason      TEXT                NOT NULL,
    failure_type        VARCHAR(100)        NOT NULL,
    status              VARCHAR(20)         NOT NULL CHECK (status IN ('PENDING', 'RESOLVED', 'IGNORED')) DEFAULT 'PENDING',
    retry_count         INT                 NOT NULL DEFAULT 0 CHECK (retry_count >= 0),
    created_on          TIMESTAMP           NOT NULL DEFAULT NOW(),
    last_retry_on       TIMESTAMP,
    resolved_on         TIMESTAMP,
    admin_notes         TEXT
);

-- Indexes for efficient querying
CREATE INDEX IF NOT EXISTS idx_dlq_status ON dlq_messages (status);
CREATE INDEX IF NOT EXISTS idx_dlq_created_on ON dlq_messages (created_on);
CREATE INDEX IF NOT EXISTS idx_dlq_order_id ON dlq_messages (original_order_id);
CREATE INDEX IF NOT EXISTS idx_dlq_failure_type ON dlq_messages (failure_type);

-- Combined index for common filtered queries
CREATE INDEX IF NOT EXISTS idx_dlq_status_created ON dlq_messages (status, created_on DESC);

COMMENT ON TABLE dlq_messages IS 'Dead-Letter Queue for failed order messages requiring administrative review and replay';
COMMENT ON COLUMN dlq_messages.id IS 'Unique identifier for this DLQ message';
COMMENT ON COLUMN dlq_messages.original_order_id IS 'Reference to the original order ID (may be null if failure occurs during deserialization)';
COMMENT ON COLUMN dlq_messages.original_message IS 'Serialized original OrderEvent message in JSON format for replay capability';
COMMENT ON COLUMN dlq_messages.failure_reason IS 'Full failure reason including exception message and stack trace';
COMMENT ON COLUMN dlq_messages.failure_type IS 'Exception type name for categorization and filtering';
COMMENT ON COLUMN dlq_messages.status IS 'PENDING = awaiting review/replay, RESOLVED = successfully replayed, IGNORED = administratively dismissed';
COMMENT ON COLUMN dlq_messages.retry_count IS 'Number of replay attempts made by administrators';
COMMENT ON COLUMN dlq_messages.created_on IS 'Timestamp when message was captured in DLQ';
COMMENT ON COLUMN dlq_messages.last_retry_on IS 'Timestamp of last replay attempt';
COMMENT ON COLUMN dlq_messages.resolved_on IS 'Timestamp when message status changed to RESOLVED or IGNORED';
COMMENT ON COLUMN dlq_messages.admin_notes IS 'Administrative notes explaining dismissal or replay context';
