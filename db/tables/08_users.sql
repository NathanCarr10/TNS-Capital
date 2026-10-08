-- Users table for auth service - stores registration credentials with hashed passwords
CREATE TABLE IF NOT EXISTS users (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username        VARCHAR(255)        NOT NULL UNIQUE,
    password_hash   VARCHAR(255)        NOT NULL,
    role            VARCHAR(20)         NOT NULL CHECK (role IN ('USER', 'ADMIN')) DEFAULT 'USER',
    account_id      BIGINT              UNIQUE REFERENCES accounts(id),
    created_at      TIMESTAMP           NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP           NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE users IS 'User accounts with Argon2id password hashes (plaintext never stored).';
COMMENT ON COLUMN users.password_hash IS 'Complete Argon2id hash string: $argon2id$v=19$m=65540,t=3,p=4$salt$hash';
COMMENT ON COLUMN users.role IS 'USER can only access their own account; ADMIN can access every account.';
COMMENT ON COLUMN users.account_id IS 'Trading account owned by this user, opened on registration; NULL for admins.';
