import { expect, test } from '@playwright/test';

import { depositViaUi, signInNewTrader, submitOrder } from './support';

// Orders go over Kafka to the broker simulator, which fills them after a delay (4-5 s in
// ci/e2e-tests.sh, so an order stays open long enough to cancel)
const FILL_TIMEOUT = 60_000;

test.describe('Trading', () => {
  test('a new trader deposits cash, and a BUY fills and shows in history and holdings', async ({ page, request }) => {
    await signInNewTrader(page, request);
    const account = page.locator('.account-card');
    await expect(account).toContainText('$0.00');

    // New accounts open with no cash
    await depositViaUi(page, 10_000);
    await expect(account).toContainText('$10,000.00');

    const ticket = await submitOrder(page, { side: 'BUY', symbol: 'AAPL', quantity: 2, price: 100 });
    await expect(ticket.locator('.note.success')).toContainText('Order FILLED: BUY 2 AAPL', { timeout: FILL_TIMEOUT });

    await page.goto('/orders');
    const historyRow = page.locator('.history-table tbody tr', { hasText: 'AAPL' });
    await expect(historyRow).toHaveCount(1);
    await expect(historyRow.locator('.side-buy')).toHaveText('BUY');
    await expect(historyRow.locator('.status-badge')).toHaveText('FILLED');

    await page.goto('/portfolio');
    await expect(page.locator('.holdings-table tbody tr', { hasText: 'AAPL' })).toBeVisible();
    // 2 x at most $100 (the broker may fill below the limit, never above)
    await expect(account).not.toContainText('$10,000.00');
  });

  test('a trader sells part of a holding', async ({ page, request }) => {
    await signInNewTrader(page, request);
    await depositViaUi(page, 1_000);
    const buy = await submitOrder(page, { side: 'BUY', symbol: 'MSFT', quantity: 3, price: 100 });
    await expect(buy.locator('.note.success')).toContainText('Order FILLED: BUY 3 MSFT', { timeout: FILL_TIMEOUT });

    const sell = await submitOrder(page, { side: 'SELL', symbol: 'MSFT', quantity: 2, price: 90 });
    await expect(sell.locator('.note.success')).toContainText('Order FILLED: SELL 2 MSFT', { timeout: FILL_TIMEOUT });

    await page.goto('/portfolio');
    const holding = page.locator('.holdings-table tbody tr', { hasText: 'MSFT' });
    await expect(holding.locator('td').nth(1)).toHaveText('1');
  });

  test('a BUY the trader cannot afford is rejected', async ({ page, request }) => {
    await signInNewTrader(page, request);

    // No deposit: the account has $0
    const ticket = await submitOrder(page, { side: 'BUY', symbol: 'AAPL', quantity: 1, price: 100 });
    await expect(ticket.locator('.hint.warning')).toHaveText('The estimated cost is more than your available cash.');
    await expect(ticket.locator('.note.error')).toHaveText('Insufficient funds (ORD-400).', { timeout: FILL_TIMEOUT });

    await page.goto('/orders');
    const historyRow = page.locator('.history-table tbody tr', { hasText: 'AAPL' });
    await expect(historyRow.locator('.status-badge')).toHaveText('REJECTED');
  });

  test('a trader cancels an order before it fills', async ({ page, request }) => {
    await signInNewTrader(page, request);
    await depositViaUi(page, 1_000);

    const ticket = await submitOrder(page, { side: 'BUY', symbol: 'AAPL', quantity: 1, price: 100 });
    await expect(ticket.locator('.note.success')).toContainText('Tracking order');

    await page.goto('/orders');
    const historyRow = page.locator('.history-table tbody tr', { hasText: 'AAPL' });
    await expect(historyRow.locator('.status-badge')).toHaveText('Open');
    await historyRow.getByRole('button', { name: 'Cancel BUY 1 AAPL' }).click();
    await expect(historyRow.locator('.status-badge')).toHaveText('CANCELLED');

    // The broker's fill arrives after the cancel and is ignored: no cash is spent
    await page.waitForTimeout(6_000);
    await page.goto('/portfolio');
    await expect(page.locator('.account-card')).toContainText('$1,000.00');
  });
});
