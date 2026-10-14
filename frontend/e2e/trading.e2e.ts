import { expect, test } from '@playwright/test';

import { depositViaApi, newTrader, registerViaApi, submitLogin } from './support';

test.describe('Trading', () => {
  test('a BUY order is filled and shows in order history and holdings', async ({ page, request }) => {
    const trader = newTrader();
    const accountId = await registerViaApi(request, trader);
    await depositViaApi(request, trader, accountId, 10_000);

    await page.goto('/login');
    await submitLogin(page, trader.username, trader.password);
    const account = page.locator('.account-card');
    await expect(account).toContainText('$10,000.00');

    await page.goto('/trade/AAPL');
    const ticket = page.locator('form.ticket-body');
    await expect(ticket.getByRole('radio', { name: 'BUY' })).toHaveAttribute('aria-checked', 'true');
    await ticket.getByLabel('Quantity').fill('2');
    // Our own limit, so the test doesn't depend on market data being configured
    await ticket.getByLabel('Limit price').fill('100');
    await ticket.getByRole('button', { name: 'Buy 2 AAPL' }).click();

    // Placed over Kafka, executed by the broker simulator, settled by the API
    await expect(ticket.locator('.note.success')).toContainText('Order FILLED: BUY 2 AAPL', { timeout: 60_000 });

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
});
