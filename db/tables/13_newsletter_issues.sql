-- NEWSLETTER_ISSUES: the weekly market newsletter, one row per week. Each row is
-- a snapshot of what was published: the week's biggest movers and the news about
-- them, stored as JSONB because an issue is written once and always read whole
-- (docs/DECISIONS.md #17). The API may insert and read issues but never change them.
CREATE TABLE IF NOT EXISTS newsletter_issues (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    week_start    DATE            NOT NULL UNIQUE,   -- Monday of the week covered, so a rerun cannot publish the week twice
    generated_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
    gainers       JSONB           NOT NULL,
    losers        JSONB           NOT NULL,
    news          JSONB           NOT NULL,
    CONSTRAINT ck_newsletter_issues_week_start_monday CHECK (EXTRACT(ISODOW FROM week_start) = 1),
    CONSTRAINT ck_newsletter_issues_gainers_array CHECK (jsonb_typeof(gainers) = 'array'),
    CONSTRAINT ck_newsletter_issues_losers_array CHECK (jsonb_typeof(losers) = 'array'),
    CONSTRAINT ck_newsletter_issues_news_array CHECK (jsonb_typeof(news) = 'array')
);

-- No foreign keys to instruments: a published issue must survive an instrument
-- being renamed or removed. The UNIQUE index on week_start also serves the
-- newest-first listing.

COMMENT ON TABLE newsletter_issues IS 'Weekly market newsletter issues (top movers and their news), immutable once published.';
COMMENT ON COLUMN newsletter_issues.gainers IS 'Top gainers, best first: [{symbol, name, startClose, endClose, changePct}]';
COMMENT ON COLUMN newsletter_issues.losers IS 'Top losers, worst first: [{symbol, name, startClose, endClose, changePct}]';
COMMENT ON COLUMN newsletter_issues.news IS 'Alpaca News headlines about the movers: [{symbol, headline, source, url, publishedAt}]; empty when news was unavailable';
