-- Instruments table
CREATE TABLE IF NOT EXISTS instruments (
    symbol      VARCHAR(20)     PRIMARY KEY,
    name        VARCHAR(255)    NOT NULL,
    asset_class VARCHAR(20)     NOT NULL,
    currency    CHAR(3)         NOT NULL,
    tradable    BOOLEAN         NOT NULL DEFAULT TRUE
);

COMMENT ON TABLE instruments IS 'Tradable instruments';