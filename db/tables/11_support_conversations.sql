CREATE TABLE IF NOT EXISTS support_conversations (
    id UUID PRIMARY KEY,
    customer_username VARCHAR(255) NOT NULL REFERENCES users(username),
    subject VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('OPEN', 'CLOSED')) DEFAULT 'OPEN',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_support_conversations_customer ON support_conversations(customer_username, updated_at DESC);