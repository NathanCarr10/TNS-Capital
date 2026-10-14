# Frontend

This project was generated using [Angular CLI](https://github.com/angular/angular-cli) version 19.2.27.

## Development server

To start a local development server, run:

```bash
ng serve
```

Once the server is running, open your browser and navigate to `http://localhost:4200/`. The application will automatically reload whenever you modify any of the source files.

## API clients

The services and models in `src/app/api/` are generated from the OpenAPI specs in `../contracts/` and are not committed:

| Spec | Generated into | Used by |
| --- | --- | --- |
| `contracts/auth-api.yaml` | `src/app/api/auth` | `core/auth/auth.service.ts`, `core/trading/trading.service.ts` |
| `contracts/trading-api.json` | `src/app/api/trading` | `core/trading/trading.service.ts` (accounts, positions, orders), `market-data.service.ts` (prices) |

`npm start`, `npm run build`, `npm test` and `npm run typecheck` regenerate them first. Generating needs Java 11+.

When a backend API changes:

```bash
npm run fetch:trading-spec   # needs the Spring Boot app on :3000 (TRADING_API_URL to override)
npm run typecheck            # regenerates, then fails on any code that no longer matches
```

Auth spec changes are made by hand in `contracts/auth-api.yaml`. Which response fields are required or nullable comes from `@Schema` on the backend DTOs. Call the APIs through the generated services, never with `HttpClient` directly. `core/api/api-types.spec.ts` checks that the generated types still reject invalid requests.

## Admins and the analytics dashboard

Signing in as an admin opens the analytics dashboard (the Streamlit app in `dashboard/`,
`docker compose up -d dashboard`), where the admin signs in again; its **Trading dashboard**
button comes back here. Admins have no trading account, so for them `/portfolio` is a read-only
account viewer: an account picker and an **Analytics** button in the header, and the order ticket on `/trade` is read-only.
If a signed-out user opens a protected page, the login returns them to it (`?returnUrl=`).
The dashboard is served on this app's own origin at `/analytics/`: nginx (and `ng serve`'s
`proxy.conf.json`) forward it to the Streamlit container, so the link works wherever the app is
opened from (`ANALYTICS_DASHBOARD_URL` in `src/app/core/analytics/analytics-dashboard.service.ts`). Why admins sign in twice: see
decision 12 in [docs/DECISIONS.md](../docs/DECISIONS.md).

## Code scaffolding

Angular CLI includes powerful code scaffolding tools. To generate a new component, run:

```bash
ng generate component component-name
```

For a complete list of available schematics (such as `components`, `directives`, or `pipes`), run:

```bash
ng generate --help
```

## Building

To build the project run:

```bash
ng build
```

This will compile your project and store the build artifacts in the `dist/` directory. By default, the production build optimizes your application for performance and speed.

## Running unit tests

To execute unit tests with the [Karma](https://karma-runner.github.io) test runner, use the following command:

```bash
ng test
```

## End-to-end tests

[Playwright](https://playwright.dev) drives a real browser through the core journeys in [`e2e/`](e2e):

| File | Journeys |
| --- | --- |
| `auth.e2e.ts` | The route guard sends a signed-out visitor to login and back; a new trader registers, logs in and sees their account; a wrong password shows an error |
| `trading.e2e.ts` | A new trader funds their account with the deposit form, places a BUY that fills (Kafka, broker simulator), then sees it in order history and holdings |
| `admin.e2e.ts` | The home page's Admin card and an admin's login both reach the analytics dashboard, where the admin signs in |

They need the whole stack, so the simplest way to run them is the same script as Jenkins, from the
repo root. It needs only Docker: it starts a throwaway copy of the stack with no host ports, so it
can run next to your own, and removes it afterwards.

```bash
ci/e2e-tests.sh
```

To run them against a stack that is already up (`docker compose up -d`, app on :4200):

```bash
npx playwright install chromium   # once
npm run e2e                       # E2E_BASE_URL=... for another address; add --ui to watch them
```

Each run registers new `e2e…` users. The auth service allows 10 logins a minute per client by
default (`AUTH_RATE_LIMIT`), and one run uses about 5, so wait a minute between local runs.
The HTML report is in `playwright-report/`, traces of failed tests in `test-results/`
(`npx playwright show-trace <trace.zip>`).

## Additional Resources

For more information on using the Angular CLI, including detailed command references, visit the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.
