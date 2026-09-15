-- Instruments table
DROP TABLE IF EXISTS instruments;

CREATE TABLE instruments (
    symbol      VARCHAR(20) PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    asset_class VARCHAR(20) NOT NULL,
    currency    VARCHAR(3)  NOT NULL,
    tradable    BOOLEAN NOT NULL DEFAULT TRUE
);
