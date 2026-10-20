CREATE TABLE IF NOT EXISTS support_messages (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES support_conversations(id) ON DELETE CASCADE,
    sender_username VARCHAR(255) NOT NULL REFERENCES users(username),
    sender_role VARCHAR(20) NOT NULL CHECK (sender_role IN ('CUSTOMER', 'SUPPORT')),
    body VARCHAR(4000) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_support_messages_conversation ON support_messages(conversation_id, created_at);