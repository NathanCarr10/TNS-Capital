import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end tests: a real browser against the whole stack (Angular behind nginx, auth
 * service, trading API, Kafka, broker simulator, analytics dashboard).
 *
 * E2E_BASE_URL is where the app is served: http://localhost:8086 for a local stack
 * (`docker compose up -d` or `npm start`), http://frontend:8080 inside ci/e2e-tests.sh.
 */
export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.e2e.ts',
  globalSetup: './e2e/global-setup.ts',
  // The journeys share one database and the auth rate limit, so they run one at a time
  workers: 1,
  fullyParallel: false,
  forbidOnly: !!process.env['CI'],
  retries: 0,
  timeout: 90_000,
  expect: { timeout: 10_000 },
  reporter: [
    ['list'],
    ['junit', { outputFile: 'e2e-results.xml' }],
    ['html', { outputFolder: 'playwright-report', open: 'never' }],
  ],
  use: {
    baseURL: process.env['E2E_BASE_URL'] ?? 'http://localhost:8086',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
