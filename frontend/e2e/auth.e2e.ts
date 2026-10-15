import { expect, test } from '@playwright/test';

import { newTrader, registerViaApi, submitLogin } from './support';

test.describe('Signing in', () => {
  test('a signed-out visitor to the portfolio is sent to login, then back to it', async ({ page, request }) => {
    const trader = newTrader();
    await registerViaApi(request, trader);

    await page.goto('/portfolio');

    await expect(page).toHaveURL(/\/login\?returnUrl=%2Fportfolio$/);
    await expect(page.getByRole('heading', { name: 'Welcome Back' })).toBeVisible();

    await submitLogin(page, trader.username, trader.password);

    await expect(page).toHaveURL(/\/portfolio$/);
    await expect(page.locator('.account-card')).toContainText(trader.holderName);
  });

  test('a new trader registers, logs in and sees their own account', async ({ page }) => {
    const trader = newTrader();

    await page.goto('/');
    await page.getByRole('link', { name: /Sign In to Trade/ }).click();
    await page.getByRole('link', { name: 'Create an account' }).click();

    await page.getByLabel('Username').fill(trader.username);
    await page.getByLabel(/Account holder name/).fill(trader.holderName);
    await page.getByLabel('Password', { exact: true }).fill(trader.password);
    await page.getByLabel('Confirm password').fill(trader.password);
    await page.getByRole('button', { name: 'Register' }).click();

    // Registering returns to login rather than signing in
    await expect(page).toHaveURL(/\/login$/);
    await submitLogin(page, trader.username, trader.password);

    await expect(page).toHaveURL(/\/portfolio$/);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Welcome, E2E');
    const account = page.locator('.account-card');
    await expect(account).toContainText(trader.holderName);
    await expect(account).toContainText('ACTIVE');
    await expect(account).toContainText('$0.00');
  });

  test('a wrong password keeps the trader on the login page with an error', async ({ page }) => {
    await page.goto('/login');

    await submitLogin(page, 'admin', 'not-the-password');

    await expect(page.locator('.server-error')).toBeVisible();
    await expect(page).toHaveURL(/\/login$/);
  });
});
