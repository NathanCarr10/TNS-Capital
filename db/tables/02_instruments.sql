-- Instruments table
CREATE TABLE IF NOT EXISTS instruments (
    symbol      VARCHAR(20)     PRIMARY KEY,
    name        VARCHAR(255)    NOT NULL,
    asset_class VARCHAR(20)     NOT NULL,
    currency    CHAR(3)         NOT NULL,
    tradable    BOOLEAN         NOT NULL DEFAULT TRUE,
    -- Latest market price from the price feed; NULL until the first price arrives
    last_price      NUMERIC(18,2)   CHECK (last_price > 0),
    last_price_on   TIMESTAMP
);

COMMENT ON TABLE instruments IS 'Tradable instruments';