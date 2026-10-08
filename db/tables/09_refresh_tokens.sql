-- Refresh tokens issued by the auth service. One row per token; a user can hold several sessions.
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  CHAR(64)    NOT NULL UNIQUE,
    family_id   UUID        NOT NULL,
    expires_at  TIMESTAMP   NOT NULL,
    used_at     TIMESTAMP,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user ON refresh_tokens (user_id);

COMMENT ON TABLE refresh_tokens IS 'Refresh tokens by SHA-256 hash (never the token itself). Each is single use: a refresh spends it and issues the next one in the same family.';
COMMENT ON COLUMN refresh_tokens.token_hash IS 'SHA-256 (hex) of the refresh token.';
COMMENT ON COLUMN refresh_tokens.family_id IS 'Login session: every token rotated from one login shares it. Reusing a spent token revokes the whole family.';
COMMENT ON COLUMN refresh_tokens.expires_at IS 'Set 7 days after the token is issued.';
COMMENT ON COLUMN refresh_tokens.used_at IS 'When the token was spent by a refresh; NULL while it can still be used.';
