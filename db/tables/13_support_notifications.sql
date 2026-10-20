CREATE TABLE IF NOT EXISTS support_notifications (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    recipient_role VARCHAR(20) NOT NULL CHECK (recipient_role IN ('CUSTOMER', 'SUPPORT')),
    recipient_username VARCHAR(255) REFERENCES users(username),
    conversation_id UUID NOT NULL REFERENCES support_conversations(id) ON DELETE CASCADE,
    message_id BIGINT NOT NULL REFERENCES support_messages(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    read_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_support_notifications_recipient ON support_notifications(recipient_role, recipient_username, read_at);