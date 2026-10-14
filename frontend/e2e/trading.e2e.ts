import { expect, test } from '@playwright/test';

import { depositViaApi, newTrader, registerViaApi, submitLogin } from './support';

test.describe('Trading', () => {
  test('a BUY order is filled and shows in order history and holdings', async ({ page, request }) => {
    const trader = newTrader();
    const accountId = await registerViaApi(request, trader);
    await depositViaApi(request, trader, accountId, 10_000);

    await page.goto('/login');
    await submitLogin(page, trader.username, trader.password);
    await expect(page.locator('.account-grid')).toContainText('$10,000.00');

    const ticket = page.locator('.order-panel');
    await ticket.getByLabel('Instrument Symbol').selectOption('AAPL');
    // Picking a symbol fetches its live price into Price; wait for that, then set our own
    // limit so the test doesn't depend on market data being configured
    await expect(ticket.getByLabel('Price')).not.toHaveAttribute('placeholder', /Fetching/);
    await expect(ticket.getByRole('radio', { name: 'BUY' })).toHaveAttribute('aria-checked', 'true');
    await ticket.getByLabel('Quantity').fill('2');
    await ticket.getByLabel('Price').fill('100');
    await ticket.getByRole('button', { name: 'Submit Order' }).click();

    // Placed over Kafka, executed by the broker simulator, settled by the API
    await expect(ticket.locator('.status.success')).toContainText('Order FILLED', { timeout: 60_000 });

    const historyRow = page.locator('.history-table tbody tr', { hasText: 'AAPL' });
    await expect(historyRow).toHaveCount(1);
    await expect(historyRow.locator('.side-buy')).toHaveText('BUY');
    await expect(historyRow.locator('.status-badge')).toHaveText('FILLED');

    await expect(page.locator('.holdings-table tbody tr', { hasText: 'AAPL' })).toBeVisible();
    // 2 x at most $100 (the broker may fill below the limit, never above)
    await expect(page.locator('.account-grid')).not.toContainText('$10,000.00');
  });
});
