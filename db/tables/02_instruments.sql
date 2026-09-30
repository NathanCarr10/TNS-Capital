-- Instruments table: tradable instruments / securities
CREATE TABLE instruments (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    asset_class VARCHAR(50) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    tradable BOOLEAN NOT NULL
);
