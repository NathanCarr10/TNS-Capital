import { expect, test } from '@playwright/test';

import { newTrader, registerViaApi, submitLogin } from './support';

test.describe('Trading', () => {
  test('a new trader funds their account, and a BUY fills and shows in history and holdings', async ({
    page,
    request,
  }) => {
    const trader = newTrader();
    await registerViaApi(request, trader);

    await page.goto('/login');
    await submitLogin(page, trader.username, trader.password);
    const account = page.locator('.account-grid');
    await expect(account).toContainText('$0.00');

    // New accounts open with no cash
    const deposit = page.locator('.deposit-panel');
    await deposit.getByLabel('Deposit Amount').fill('10000');
    await deposit.getByRole('button', { name: 'Deposit Funds' }).click();
    await expect(page.locator('.status.success')).toContainText('Deposit of $10,000.00 completed.');
    await expect(account).toContainText('$10,000.00');

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
    await expect(account).not.toContainText('$10,000.00');
  });
});
