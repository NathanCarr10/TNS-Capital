-- Users table for auth service - stores registration credentials with hashed passwords
CREATE TABLE IF NOT EXISTS users (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email           VARCHAR(320)        NOT NULL UNIQUE,
    password_hash   VARCHAR(255)        NOT NULL,
    role            VARCHAR(20)         NOT NULL CHECK (role IN ('USER', 'SUPPORT', 'ADMIN')) DEFAULT 'USER',
    account_id      BIGINT              UNIQUE REFERENCES accounts(id),
    created_at      TIMESTAMP           NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP           NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE users IS 'User accounts with Argon2id password hashes (plaintext never stored).';
COMMENT ON COLUMN users.email IS 'Sign-in identity, stored trimmed and in lower case; the access token''s sub.';
COMMENT ON COLUMN users.password_hash IS 'Complete Argon2id hash string: $argon2id$v=19$m=65540,t=3,p=4$salt$hash';
COMMENT ON COLUMN users.role IS 'USER can only access their own account; SUPPORT views every account and suspends or reactivates them in the trading app; ADMIN runs the analytics dashboard and operations (DLQ, metrics).';
COMMENT ON COLUMN users.account_id IS 'Trading account owned by this user, opened on registration; NULL for support and admin users.';

ALTER TABLE accounts ADD COLUMN IF NOT EXISTS email VARCHAR(320);
UPDATE accounts SET email = LOWER(account_number) || '@example.invalid' WHERE email IS NULL;
ALTER TABLE accounts ALTER COLUMN email SET NOT NULL;
