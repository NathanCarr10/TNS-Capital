# Market Data Provider Evaluation

**Decision:** We use [Alpaca](https://alpaca.markets) for live prices, historical prices
and development/test environments.

This document covers the providers we looked at, how they compare, why we chose Alpaca,
how it is integrated, and the risks we accept by using it.

Prices and limits were checked in October 2026. Providers change their plans often, so
check the linked pricing pages before relying on a number.

## Contents

1. [What we need from a provider](#1-what-we-need-from-a-provider)
2. [Providers evaluated](#2-providers-evaluated)
3. [Comparison](#3-comparison)
4. [Why Alpaca](#4-why-alpaca)
5. [Provider for each use](#5-provider-for-each-use)
6. [Integration approach](#6-integration-approach)
7. [Integration risks](#7-integration-risks)
8. [Acceptance criteria and test cases](#8-acceptance-criteria-and-test-cases)
9. [Sources](#9-sources)

---

## 1. What we need from a provider

TNS Capital trades US equities. Market data is used in two places:

| Where | What it needs |
|-------|---------------|
| **Trading API (Java)**: orders placed without a price are priced at the live market price | Real-time last-trade prices for every tradable instrument, pushed to us (streaming), with a REST fallback |
| **ETL pipeline (Python)**: loads daily price history into `analytics.fact_daily_prices` | Several years of daily OHLCV bars for many symbols in few requests |

And, because this is a training project with five developers:

- **Free to use.** Every developer and every CI run must be able to work without a paid plan.
- **An official, supported API.** No scraping, no unofficial libraries that can break overnight.
- **One provider for live and historical data**, so the live price on an order and the
  daily close in the analytics schema agree with each other.
- **Easy to call from both Java and Python**, without heavy SDKs.

---

## 2. Providers evaluated

The user story lists six providers. We evaluated all six.

### Yahoo Finance (`yfinance`)

An open-source Python library that reads Yahoo Finance's website endpoints. It is free and
has long history, but **it is not an official API**: Yahoo does not publish or support it,
there are no API keys or documented limits, and Yahoo blocks IP addresses that make too many
requests (`YFRateLimitError`). Breakages after Yahoo changes its site are common. There is
no Java client and no streaming feed.

*Good for one-off analysis in a notebook. Not suitable for a system that places orders.*

### Alpha Vantage

An official REST API with broad coverage (stocks, forex, crypto, fundamentals, technical
indicators). The free plan is limited to **25 requests per day** (5 per minute) and
compact output of 100 data points per request. Real-time US data needs a premium plan
($49.99–$249.99/month). There is no WebSocket streaming.

*25 requests a day is used up by one ETL run over a handful of symbols.*

### Twelve Data

An official REST and WebSocket API with global coverage. The free plan gives about **800
credits per day and 8 per minute**; WebSocket streaming and most real-time US data are on
paid plans (from about $29/month).

*A reasonable REST source, but the free plan is too small for our ETL and has no usable
streaming.*

### Polygon.io (now Massive)

High-quality data from all US exchanges, with excellent documentation, REST and WebSocket.
Polygon was renamed **Massive** in October 2025; the old `api.polygon.io` URLs still work.
The free plan is **end-of-day data at 5 requests per minute**. The Starter ($29/month) and
Developer ($79/month) plans are **15-minute delayed**; real-time data starts at the
Advanced plan (**$199/month**).

*The best data quality of the group, but real-time prices cost $199/month.*

### Alpaca

A broker whose market data API is free with any Alpaca account (including paper trading).
The free **Basic** plan gives:

- **Real-time** trades and quotes over **WebSocket and REST** from the IEX exchange,
  for up to **30 streamed symbols**
- **Historical** bars, trades and quotes back to 2016 from **all US exchanges (SIP)**,
  except the latest 15 minutes
- **200 historical API calls per minute**, with many symbols per call

The paid **Algo Trader Plus** plan ($99/month) adds real-time SIP data, unlimited
streamed symbols and 10,000 calls per minute.

### IEX Cloud

**Discontinued.** IEX Group shut IEX Cloud down on 31 August 2024. It is not an option.

---

## 3. Comparison

Free-plan figures unless stated. ✅ good fit · ⚠️ partial or paid · ❌ not available or a blocker.

| | Yahoo (`yfinance`) | Alpha Vantage | Twelve Data | Polygon / Massive | **Alpaca** | IEX Cloud |
|---|---|---|---|---|---|---|
| **Official API** | ❌ unofficial | ✅ | ✅ | ✅ | ✅ | ❌ shut down |
| **Data coverage** | Global stocks, ETFs, FX, crypto | Global stocks, FX, crypto, fundamentals | Global stocks, FX, crypto, ETFs | US stocks, options, FX, crypto | US stocks & ETFs, options, crypto | — |
| **Real-time (free)** | ⚠️ unreliable, no streaming | ❌ premium only | ⚠️ limited, no free streaming | ❌ end-of-day only | ✅ IEX feed, WebSocket + REST | — |
| **Real-time (paid)** | — | $49.99+/mo | ~$29+/mo | **$199/mo** (Advanced) | **$99/mo** (all exchanges) | — |
| **Historical data** | ✅ decades, daily | ✅ 20+ years (compact on free) | ✅ | ✅ 2+ years on free | ✅ since 2016, all US exchanges | — |
| **API limits (free)** | ❌ undocumented, IP blocks | ❌ 25/day | ⚠️ ~800/day, 8/min | ❌ 5/min | ✅ 200/min, multi-symbol | — |
| **Streaming** | ❌ | ❌ | ⚠️ paid | ⚠️ paid | ✅ free (30 symbols) | — |
| **Ease of integration** | ⚠️ Python only | ✅ simple REST | ✅ REST + SDKs | ✅ excellent docs | ✅ REST + WebSocket, plain JSON, header-key auth | — |
| **Cost for our use** | Free | Free plan too small | Free plan too small | $199/mo for real time | **Free** | — |

### How the free plans compare for our ETL

The first ETL run backfills 5 years of daily bars for every instrument. With 50 instruments:

| Provider | Requests needed | Fits the free plan? |
|----------|-----------------|---------------------|
| Alpha Vantage | 50 (one per symbol; full history needs premium output) | ❌ 25/day |
| Twelve Data | ~50 | ⚠️ yes, but uses most of the daily credits |
| Polygon / Massive | 50 | ⚠️ 10+ minutes at 5/min |
| **Alpaca** | **~7** (many symbols per call, 10,000 bars per page) | ✅ seconds |

---

## 4. Why Alpaca

### 4.1 It is the only free option with real-time streaming

Our order flow prices market orders at the live price. That needs prices pushed to us as
they change, not polled. Alpaca is the only provider here that streams real-time trades
over WebSocket on its free plan. Every other provider either charges for real time
(Polygon $199/month, Alpha Vantage $49.99+/month) or has no streaming at all.

### 4.2 One provider for live and historical data

Using the same source for both means the price an order is filled at and the daily bars
in `analytics.fact_daily_prices` come from the same data. If live prices came from one
vendor and history from another, the analytics would not agree with the trades — different
vendors handle corrections, trading hours and symbols differently.

### 4.3 Historical data is from all exchanges, free

Alpaca's free plan only limits the **latest 15 minutes** of all-exchange (SIP) data. Daily
history is always older than that, so the ETL reads full SIP history — the same quality
as a paid plan — at no cost.

### 4.4 Generous, documented limits

200 requests per minute with multiple symbols per request is far more than the ETL or the
REST fallback need. The limits are published, and a breach returns a normal HTTP `429`,
which both our clients retry. Compare Alpha Vantage's 25 per day, Polygon's 5 per minute,
or Yahoo's undocumented IP bans.

### 4.5 Easy to integrate from Java and Python

Alpaca's API is plain JSON over HTTPS and WebSocket, authenticated with two headers
(`APCA-API-KEY-ID`, `APCA-API-SECRET-KEY`). We did not need an SDK:

- **Java** uses Spring's `RestClient` and the JDK's built-in `java.net.http.WebSocket`.
- **Python** uses only the standard library (`urllib`), so the ETL container needs no
  extra packages.

### 4.6 Free for every developer and CI run

Any developer can create free keys in minutes. The paper-trading account that comes with
the keys also lets us try a real broker's order flow later without changing provider.

### 4.7 A clear upgrade path

If we need prices from all exchanges in real time, Algo Trader Plus ($99/month) is a
**configuration change** (`ALPACA_FEED=sip`), not a rewrite — half the cost of Polygon's
real-time plan.

### What we gave up

- **Coverage outside the US.** Alpaca covers US stocks, ETFs, options and crypto only.
  All our instruments are US equities, so this does not affect us today.
- **Fundamentals and indicators.** Alpha Vantage and Twelve Data offer these; we do not
  need them.
- **Data quality of a dedicated vendor.** Polygon/Massive is a specialist data company.
  For our needs (last trade and daily bars), Alpaca's data is sufficient.

---

## 5. Provider for each use

| Use | Provider | Feed | Plan | Why |
|-----|----------|------|------|-----|
| **Live data** | Alpaca | `iex` (WebSocket, REST fallback) | Basic (free) | Only free real-time stream; upgrade to `sip` on Algo Trader Plus for all exchanges |
| **Historical data** | Alpaca | `sip` (all US exchanges) | Basic (free) | Free all-exchange daily bars since 2016; same source as live prices |
| **Development / test** | Alpaca, each developer with their own free keys; mocked in unit tests | `iex` | Basic (free) | Free keys per developer; the app and ETL also run with no keys at all (see below) |

### Development and test environments

- **Unit tests never call Alpaca.** `AlpacaRestClientTest`, `AlpacaPriceStreamTest` and
  `LivePriceServiceTest` (Java) and the tests in `etl/tests/` (Python) mock the API, so
  the Jenkins build needs no keys and does not depend on Alpaca being up.
- **Running locally without keys works.** If `ALPACA_API_KEY_ID` and `ALPACA_API_SECRET_KEY`
  are blank, the live price pool is turned off (orders must include a `price`) and the
  ETL skips the price job with a warning.
- **Each developer uses their own keys.** The free plan allows one stream connection per
  account, so with shared keys only one developer would get the live stream; the others
  would be refused and fall back to REST.

---

## 6. Integration approach

Alpaca is already integrated. Configuration is in `market-data.alpaca` in
[application.yml](../src/main/resources/application.yml) and in
[etl/config.py](../etl/config.py); keys come from `.env`.

### 6.1 Live prices (Java, `com.neueda.leap.marketdata`)

```
             WebSocket (trades, IEX)          REST /v2/stocks/trades/latest
Alpaca  ─────────────────────────────┐   ┌──────────────────────────────┐
                                     ▼   ▼                              │
                        AlpacaPriceStream   AlpacaRestClient ───────────┘
                                     │   ▲
                              update │   │ refresh (stale or not streamed)
                                     ▼   │
                              LivePriceService  (implements PriceProvider)
                                     │
                    ┌────────────────┴────────────────┐
                    ▼                                 ▼
         POST /api/v1/orders                GET /api/v1/prices/{symbol}
         (price omitted → live price)
```

| Class | Responsibility |
|-------|----------------|
| `PriceProvider` | Interface the rest of the app depends on: `latestPrice(symbol)` |
| `LivePriceService` | Keeps the latest price per symbol in memory; refreshes over REST if a price is older than `max-price-age` (60s) or the symbol is not streamed |
| `AlpacaPriceStream` | Subscribes to live trades for up to `max-symbols` (30) instruments; reconnects with backoff (1s, 2s, 4s … up to 1 min); stops on errors a retry cannot fix (bad keys, plan limits) |
| `AlpacaRestClient` | Fetches latest trades for many symbols in one call; 5s connect/read timeouts so a slow Alpaca response cannot hang an order |
| `AlpacaProperties` | Binds `market-data.alpaca.*` |

If no live price is available, `POST /api/v1/orders` without a price returns
`503 PRC-503` instead of guessing.

### 6.2 Historical prices (Python, `etl/market_data.py`)

- Downloads daily bars (`/v2/stocks/bars`, `timeframe=1Day`) for many symbols per request,
  following `next_page_token`.
- Uses the `sip` feed and ends the request 16 minutes before now, to stay outside the free
  plan's 15-minute restriction.
- First run backfills `PRICE_BACKFILL_YEARS` (5) years; later runs start from the last
  loaded day (reloaded in case it was loaded before the close) and upsert.
- Retries `429` and `5xx` responses three times with doubling waits; fails immediately on
  `401`/`403` (wrong keys or a feed the plan does not include).
- Prices are stored **raw** (`adjustment=raw`): as traded, not adjusted for splits or
  dividends.

### 6.3 Configuration

| Variable | Default | Purpose |
|----------|---------|---------|
| `ALPACA_API_KEY_ID`, `ALPACA_API_SECRET_KEY` | blank | Alpaca keys. Blank turns live prices off and skips the ETL price job |
| `ALPACA_FEED` | `iex` | Live feed. `sip` needs Algo Trader Plus |
| `PRICE_HISTORY_FEED` | `sip` | ETL history feed |
| `PRICE_BACKFILL_YEARS` | `5` | Years of history on the first ETL run |

### 6.4 Keeping the provider replaceable

Only the `marketdata` package and `etl/market_data.py` know about Alpaca. Orders and
controllers depend on the `PriceProvider` interface, and the ETL stores bars in its own
schema. Switching to another provider means writing a new `PriceProvider` and a new
extract function, with no change to the order flow or the analytics schema.

---

## 7. Integration risks

| # | Risk | Impact | Likelihood | Mitigation |
|---|------|--------|------------|------------|
| 1 | **Free live feed is IEX only** (about 2.5% of US volume), so the last IEX trade can differ slightly from the consolidated price, and can be stale for thinly traded stocks | Orders priced a few cents off the market | High | Acceptable for a simulated trading system. Stale prices (>60s) are refreshed over REST. Switch to `sip` on Algo Trader Plus for production-grade prices |
| 2 | **30 streamed symbols** on the free plan | Instruments beyond 30 are not streamed | Medium (as instruments grow) | Symbols not streamed are priced over REST on demand; upgrade removes the limit |
| 3 | **One stream connection per account** | A second app instance, or a second developer sharing keys, is refused (error 406) and prices every order over REST | Medium | One key pair per developer and per environment. The refused instance keeps retrying and still works over REST. Scaling out needs the paid plan or a single price-publisher service (e.g. via the `market-data` Kafka topic) |
| 4 | **Alpaca outage or network failure** | No live prices | Low | Stream reconnects with backoff; REST fallback; orders without a price return `503 PRC-503` rather than using a wrong price; orders with an explicit price still work |
| 5 | **Rate limits** (200 req/min) | ETL or REST fallback throttled | Low | Multi-symbol requests keep usage low; `429` is retried with backoff |
| 6 | **Unadjusted historical prices** | Charts and returns show jumps on split dates | Medium | Documented; switch to `adjustment=all` if analytics need adjusted series |
| 7 | **Leaked API keys** | Account misuse | Low | Keys only in `.env` / Jenkins credentials, never committed (see [SECURITY_GUIDE.md](SECURITY_GUIDE.md)); market-data-only keys from a paper account carry no money |
| 8 | **Vendor lock-in / plan changes** | Alpaca changes or removes the free plan | Low | `PriceProvider` interface and isolated ETL extract (section 6.4); Polygon/Massive or Twelve Data are the fallback candidates |
| 9 | **US markets only** | Cannot add non-US instruments | Low (not in scope) | Revisit provider choice if non-US instruments are added |
| 10 | **Market hours** | No live trades outside US trading hours | Certain | Last known price is used; `max-price-age` triggers a REST refresh, which returns the last trade |

---

## 8. Acceptance criteria and test cases

| Acceptance criterion | Status | Where |
|----------------------|--------|-------|
| Research at least 3 market data providers | ✅ 6 evaluated | [Section 2](#2-providers-evaluated) |
| Compare coverage, real-time, historical, cost, API limits, ease of integration | ✅ | [Section 3](#3-comparison) |
| Document recommended provider(s) | ✅ Alpaca | [Section 4](#4-why-alpaca) |
| Identify provider for live, historical, dev/test | ✅ | [Section 5](#5-provider-for-each-use) |
| Present findings during sprint review | ⏳ | This document is the basis for the presentation |

| Test case | Status |
|-----------|--------|
| Provider evaluation completed | ✅ Sections 2–3 |
| Recommendation approved by team | ⏳ To be confirmed at sprint review |
| Integration risks identified | ✅ Section 7 |

| Deliverable | Where |
|-------------|-------|
| Comparison document | Sections 2–3 |
| Recommendation | Sections 4–5 |
| Integration approach | Section 6 |

---

## 9. Sources

- Alpaca — [About Market Data API](https://docs.alpaca.markets/docs/about-market-data-api) (plans, feeds, limits)
- Alpaca — [Historical stock data](https://docs.alpaca.markets/docs/historical-stock-data-1)
- Polygon / Massive — [pricing](https://massive.com/pricing); rename coverage: [apicostcalc.com](https://apicostcalc.com/blog/polygon-massive-rebrand-api-pricing.html)
- Alpha Vantage — [premium plans](https://www.alphavantage.co/premium/)
- Twelve Data — [pricing](https://twelvedata.com/pricing)
- IEX Cloud shutdown — [Alpha Vantage migration guide](https://www.alphavantage.co/iexcloud_shutdown_analysis_and_migration/)
- yfinance rate limiting — [GitHub discussion #2431](https://github.com/ranaroussi/yfinance/discussions/2431)
