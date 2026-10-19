import { expect, test } from '@playwright/test';

import { signInNewTrader } from './support';

// The e2e stack has no market data keys and the ETL does not run in it, so there is no
// price history to backtest and no day to replay: the journeys check those answers, and
// the end-to-end trading of a replay is covered by StrategyServiceIT
test.describe('Strategy Lab', () => {
  test('a trader saves a strategy, switches it on and off, and deletes it', async ({ page, request }) => {
    await signInNewTrader(page, request);
    await page.getByRole('link', { name: 'Strategy Lab' }).click();
    await expect(page.getByRole('heading', { name: 'Automated strategies' })).toBeVisible();
    await expect(page.getByText('No strategies yet')).toBeVisible();

    await page.getByLabel('Instrument').selectOption('AAPL');
    await page.getByLabel('Short average (bars)').fill('5');
    await page.getByLabel('Long average (bars)').fill('20');
    await page.getByLabel('Name').fill('Fast MA 5/20');
    await page.getByLabel('Shares per order').fill('2');
    await page.getByRole('button', { name: 'Save strategy' }).click();

    const row = page.locator('.data-table tbody tr', { hasText: 'Fast MA 5/20' });
    await expect(row).toContainText('SMA 5 / SMA 20 crossover');
    await expect(row.locator('.badge')).toHaveText('Off');

    await row.getByRole('button', { name: 'Switch on' }).click();
    await expect(row.locator('.badge')).toHaveText('On');
    await expect(page.locator('.toast.success', { hasText: 'Fast MA 5/20 is on' })).toBeVisible();
    // Nothing has streamed AAPL prices to the strategy service yet
    await expect(page.getByText('No AAPL prices received yet')).toBeVisible();

    await row.getByRole('button', { name: 'Switch off' }).click();
    await expect(row.locator('.badge')).toHaveText('Off');

    await row.getByRole('button', { name: 'Delete Fast MA 5/20' }).click();
    await expect(page.getByText('No strategies yet')).toBeVisible();
  });

  test('a backtest and a replay explain what they need', async ({ page, request }) => {
    await signInNewTrader(page, request);
    await page.goto('/strategies');

    await page.getByRole('button', { name: 'Run backtest' }).click();
    await expect(page.locator('.note.error')).toContainText('No price history yet');

    await page.getByRole('button', { name: /^Replay / }).click();
    await expect(page.getByRole('alert')).toContainText('Market data is unavailable');
  });

  test('a name with script characters is refused before it is sent', async ({ page, request }) => {
    await signInNewTrader(page, request);
    await page.goto('/strategies');

    await page.getByLabel('Name').fill('<img src=x>');
    await page.getByRole('button', { name: 'Save strategy' }).click();

    await expect(page.locator('.field-error')).toContainText("letters, numbers, spaces or . , ' / _ -");
    await expect(page.getByText('No strategies yet')).toBeVisible();
  });
});
