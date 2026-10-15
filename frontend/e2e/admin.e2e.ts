import { Page, expect, test } from '@playwright/test';

import { ADMIN, submitLogin } from './support';

/** Streamlit renders its sign-in form after the page's WebSocket connects. */
async function signInToAnalytics(page: Page): Promise<void> {
  await expect(page.getByText('Admins only. Sign in with your TNS Capital account.')).toBeVisible({
    timeout: 30_000,
  });
  await page.getByLabel('Username').fill(ADMIN.username);
  // exact: Streamlit also has a "Show password" button
  await page.getByLabel('Password', { exact: true }).fill(ADMIN.password);
  await page.getByRole('button', { name: 'Sign in' }).click();
}

test.describe('Admin', () => {
  test.beforeEach(() => {
    expect(ADMIN.password, 'Set E2E_ADMIN_PASSWORD to the ADMIN_PASSWORD in your .env').not.toBe('');
  });

  test("the home page's admin link opens the analytics dashboard, where an admin signs in", async ({ page }) => {
    await page.goto('/');
    await page.getByRole('link', { name: 'Open admin analytics' }).click();

    // Served on the app's own origin, so this works wherever the app is opened from
    await expect(page).toHaveURL(/\/analytics\/$/);
    await signInToAnalytics(page);

    await expect(page.getByText(`Signed in as ${ADMIN.username}`)).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText('Key insights')).toBeVisible();
    await expect(page.getByRole('link', { name: 'Trading dashboard' })).toHaveAttribute('href', '/portfolio');
  });

  test('an admin logging in to the trading app is taken to the analytics dashboard', async ({ page }) => {
    await page.goto('/login');
    await submitLogin(page, ADMIN.username, ADMIN.password);

    await expect(page).toHaveURL(/\/analytics\/$/);
    await expect(page.getByText('Admins only. Sign in with your TNS Capital account.')).toBeVisible({
      timeout: 30_000,
    });
  });
});
