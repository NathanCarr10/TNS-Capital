# TNS Capital Web App

The Angular 19 frontend of TNS Capital. In Docker it is built into static files and served by
nginx, which also forwards the APIs and the analytics dashboard, so the whole platform is reached
on one origin (http://localhost:8086 by default).

| Path | Forwarded to |
| --- | --- |
| `/auth/` | Auth service (`auth-service:4000`) |
| `/api/v1/strategies` | Strategy service (`strategy-service:8087`) |
| `/api/` | Trading API (`app:3000`) |
| `/analytics/` | Analytics dashboard (`dashboard:8501`, Streamlit) |

nginx also adds the security headers, including the Content Security Policy
([security-headers.conf](security-headers.conf); see
[docs/SECURITY_GUIDE.md](../docs/SECURITY_GUIDE.md#frontend-nginx-and-xss)).

On AWS the same build is served from S3 through CloudFront, which forwards the same paths and
sends the same headers in place of nginx ([infra/README.md](../infra/README.md)). If you change
the CSP in `security-headers.conf`, change it in `infra/frontend.tf` too.
`infra/scripts/publish-frontend.sh` builds the app, uploads it and invalidates CloudFront.

## Pages

| Route | Page | Who |
| --- | --- | --- |
| `/` | Welcome page | Everyone |
| `/login`, `/register` | Sign in with an email and password; register a trader, which opens their trading account | Everyone |
| `/portfolio` | Cash, holdings and portfolio value. For support staff, a read-only account viewer: pick any account, and suspend or reactivate it | Traders, support |
| `/trade/:symbol` | Price chart, news ticker and the order ticket | Traders |
| `/orders` | Order history, and cancelling an order that has not filled yet | Traders, support |
| `/strategies` | Strategy Lab: backtest, save and switch on automated strategies, replay a past day | Traders (support: read-only) |
| `/market-movers` | The weekly newsletter of top gainers and losers; support can preview this week's issue | Traders, support |
| `/settings` | Account details, renaming the holder, and display preferences (reduce motion, high contrast, text size, table density) | Traders |
| `/support` | Support Inbox: answer traders' chat conversations | Support |
| `/dead-letters` | Review, replay and dismiss failed orders in the dead-letter queue | Support |

Traders reach support through the chat widget on every signed-in page. Admins have no page here:
signing in as an admin opens the analytics dashboard instead (`notAdminGuard`), where the admin
signs in again (see decision 12 in [docs/DECISIONS.md](../docs/DECISIONS.md)). If a signed-out
user opens a protected page, the login returns them to it afterwards (`?returnUrl=`).

## Project layout

| Folder | Contents |
| --- | --- |
| `src/app/features/` | One folder per page (see the table above) |
| `src/app/layout/` | The signed-in shell and its sidebar, ticker tape, symbol search, market status, support widget and toasts |
| `src/app/core/` | Services and guards: auth, trading, strategy, support, analytics, notifications, theme, accessibility, validation |
| `src/app/api/` | The generated API clients (not committed; see below) |
| `e2e/` | Playwright end-to-end tests |

## Development server

From this folder, with the backend running in Docker (`docker compose up -d` from the repo root):

```bash
npm ci
npm start
```

`npm start` generates the API clients and runs `ng serve` on `http://<host>:8086/` (it listens on
`0.0.0.0`, set in `angular.json`), reloading on every change. `proxy.conf.json` forwards the same
paths as nginx to the services' ports on localhost. Stop the `frontend` container first
(`docker compose stop frontend`) so port 8086 is free. `ng serve` does not send the nginx
security headers, so check CSP changes against the `frontend` container.

## API clients

The services and models in `src/app/api/` are generated from the OpenAPI specs in `../contracts/` and are not committed:

| Spec | Generated into | Used by |
| --- | --- | --- |
| `contracts/auth-api.yaml` | `src/app/api/auth` | `core/auth/auth.service.ts`, `core/trading/trading.service.ts` |
| `contracts/trading-api.json` | `src/app/api/trading` | `core/trading/trading.service.ts` (accounts, positions, orders), `market-data.service.ts` (prices), `core/strategy/strategy-lab.service.ts` (market replay) |
| `contracts/strategy-api.json` | `src/app/api/strategy` | `core/strategy/strategy-lab.service.ts` (Strategy Lab), `core/strategy/strategy-activity.service.ts` (strategy trade toasts) |

`npm start`, `npm run build`, `npm test` and `npm run typecheck` regenerate them first. Generating needs Java 11+.

When a backend API changes:

```bash
npm run fetch:trading-spec   # needs the Spring Boot app on :3000 (TRADING_API_URL to override)
npm run typecheck            # regenerates, then fails on any code that no longer matches
```

The strategy service's spec is checked against the running service by `StrategyServiceIT`; after an API change, copy it over as that test's failure message says. Auth spec changes are made by hand in `contracts/auth-api.yaml`. Which response fields are required or nullable comes from `@Schema` on the backend DTOs. Call the APIs through the generated services, never with `HttpClient` directly. `core/api/api-types.spec.ts` checks that the generated types still reject invalid requests.

## Building

```bash
npm run build
```

The production build goes to `dist/`. The `Dockerfile` runs the same build and copies the result
into the nginx image, with `nginx.conf` and `security-headers.conf`.

## Unit tests

```bash
npm test                 # Karma and Jasmine in Chrome, watching for changes
npm run test:headless    # one run in headless Chromium
```

`test:headless` uses the Chromium that Playwright installs (`npx playwright install chromium`).
Jenkins runs `npm test -- --watch=false --browsers=ChromeHeadlessNoSandbox --code-coverage` in the
Playwright image, which writes coverage to `coverage/frontend/` for SonarQube.

## End-to-end tests

[Playwright](https://playwright.dev) drives a real browser through the core journeys in [`e2e/`](e2e):

| File | Journeys |
| --- | --- |
| `auth.e2e.ts` | The route guard sends a signed-out visitor to login and back; a new trader registers, logs in and sees their account; a wrong password shows an error |
| `trading.e2e.ts` | A new trader deposits cash, places a BUY that fills (Kafka, broker simulator) and sees it in order history and holdings; sells part of a holding; a BUY they cannot afford is rejected (ORD-400); an open order is cancelled before it fills |
| `settings.e2e.ts` | A trader sees their account details on the Settings page and renames the account holder |
| `strategy-lab.e2e.ts` | A trader saves a strategy, switches it on and off, and deletes it; a backtest and a replay explain what they need; a name with script characters is refused before it is sent |
| `admin.e2e.ts` | The home page's admin link reaches the analytics dashboard, where the admin signs in; an admin who signs in to the trading app is sent to the dashboard; support sees the read-only account viewer and suspends and reactivates an account; the dashboard turns support away |

They need the whole stack, so the simplest way to run them is the same script as Jenkins, from the
repo root. It needs only Docker: it starts a throwaway copy of the stack with no host ports, so it
can run next to your own, and removes it afterwards.

```bash
ci/e2e-tests.sh
```

To run them against a stack that is already up (`docker compose up -d`, app on :8086):

```bash
npx playwright install chromium   # once
# The admin and support journeys sign in with your ADMIN_PASSWORD and SUPPORT_PASSWORD, from the repo's .env
source ../.env && E2E_ADMIN_PASSWORD="$ADMIN_PASSWORD" E2E_SUPPORT_PASSWORD="$SUPPORT_PASSWORD" npm run e2e   # E2E_BASE_URL=... for another address; add --ui to watch
```

If you changed `ADMIN_EMAIL` or `SUPPORT_EMAIL`, also set `E2E_ADMIN_EMAIL` and `E2E_SUPPORT_EMAIL`.

Each run registers new `e2e…` users; afterwards `e2e/global-teardown.ts` deletes them, with their
accounts and orders, from your local stack's database (it needs `docker` on the machine running
the tests). The auth service allows 10 logins a minute per client by default (`AUTH_RATE_LIMIT`),
and one run uses about 9, so wait a minute between local runs.
The HTML report is in `playwright-report/`, traces of failed tests in `test-results/`
(`npx playwright show-trace <trace.zip>`).
