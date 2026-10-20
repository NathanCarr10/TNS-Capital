import { execFileSync } from 'node:child_process';

/** Matches the emails newTrader() hands out (support.ts), and nothing seeded. */
const E2E_EMAIL = '^e2e[0-9]+@example\\.com$';

/**
 * Deletes every trader the journeys registered, with their account and trading activity.
 * Only order_id/account_id link most of these tables (the audit tables have no foreign keys),
 * so each one is cleared explicitly.
 */
const CLEANUP_SQL = `
BEGIN;
CREATE TEMP TABLE e2e_accounts ON COMMIT DROP AS
    SELECT account_id AS id FROM users WHERE email ~ '${E2E_EMAIL}' AND account_id IS NOT NULL;
CREATE TEMP TABLE e2e_orders ON COMMIT DROP AS
    SELECT id FROM orders WHERE account_id IN (SELECT id FROM e2e_accounts)
    UNION SELECT order_id FROM order_history WHERE account_id IN (SELECT id FROM e2e_accounts);
-- refresh_tokens go with their user (ON DELETE CASCADE)
DELETE FROM users WHERE email ~ '${E2E_EMAIL}';
DELETE FROM executions WHERE order_id IN (SELECT id FROM e2e_orders);
DELETE FROM dlq_messages WHERE original_order_id IN (SELECT id FROM e2e_orders);
DELETE FROM orders WHERE id IN (SELECT id FROM e2e_orders);
DELETE FROM order_history WHERE account_id IN (SELECT id FROM e2e_accounts);
DELETE FROM trade_event_log WHERE account_id IN (SELECT id FROM e2e_accounts);
DELETE FROM positions WHERE account_id IN (SELECT id FROM e2e_accounts);
DELETE FROM accounts WHERE id IN (SELECT id FROM e2e_accounts);
COMMIT;
`;

/**
 * Removes the journeys' traders from a local stack's database, so runs against a developer's
 * own stack (docker compose up) don't leave them behind. ci/e2e-tests.sh throws its whole
 * stack away instead, so this does nothing under CI.
 */
export default async function globalTeardown(): Promise<void> {
  if (process.env['CI']) {
    return;
  }

  // The local stack's Postgres is the one publishing 5432; the CI stack's publishes nothing
  const container = execFileSync(
    'docker',
    ['ps', '--quiet', '--filter', 'label=com.docker.compose.service=postgres', '--filter', 'publish=5432'],
    { encoding: 'utf8' }
  )
    .split('\n')[0]
    .trim();
  if (!container) {
    console.warn('E2E teardown: no local Postgres container publishing 5432, so the test users were left in place');
    return;
  }

  execFileSync(
    'docker',
    ['exec', '-i', container, 'sh', '-c', 'psql -U "$POSTGRES_USER" -d tns_capital -v ON_ERROR_STOP=1 -q'],
    { input: CLEANUP_SQL, stdio: ['pipe', 'inherit', 'inherit'] }
  );
}
