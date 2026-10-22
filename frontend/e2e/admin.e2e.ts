import { Page, expect, test } from '@playwright/test';

import { ADMIN, SUPPORT, submitLogin } from './support';

/** Streamlit renders its sign-in form after the page's WebSocket connects. */
async function signInToAnalytics(page: Page, user: { email: string; password: string }): Promise<void> {
  await expect(page.getByText('Admins only. Sign in with your TNS Capital account.')).toBeVisible({
    timeout: 30_000,
  });
  await page.getByLabel('Email').fill(user.email);
  // exact: Streamlit also has a "Show password" button
  await page.getByLabel('Password', { exact: true }).fill(user.password);
  await page.getByRole('button', { name: 'Sign in' }).click();
}

// The two staff roles are kept apart: ADMIN runs the analytics dashboard, SUPPORT works on
// traders' accounts in the trading app (docs/DECISIONS.md, "Roles")
test.describe('Admin', () => {
  test.beforeEach(() => {
    expect(ADMIN.password, 'Set E2E_ADMIN_PASSWORD to the ADMIN_PASSWORD in your .env').not.toBe('');
  });

  test("the home page's admin link opens the analytics dashboard, where an admin signs in", async ({ page }) => {
    await page.goto('/');
    await page.getByRole('link', { name: 'Open admin analytics' }).click();

    // Served on the app's own origin, so this works wherever the app is opened from
    await expect(page).toHaveURL(/\/analytics\/$/);
    await signInToAnalytics(page, ADMIN);

    await expect(page.getByText(`Signed in as ${ADMIN.email}`)).toBeVisible({ timeout: 30_000 });
    // The dashboard opens on its Live view; Key insights moved to the Batch view
    await expect(page.getByRole('heading', { name: 'Live trading' })).toBeVisible();
  });

  test('an admin logging in to the trading app is sent to the analytics dashboard instead', async ({ page }) => {
    await page.goto('/login');
    await submitLogin(page, ADMIN.email, ADMIN.password);

    await expect(page).toHaveURL(/\/analytics\/$/);
    await expect(page.getByText('Admins only. Sign in with your TNS Capital account.')).toBeVisible({
      timeout: 30_000,
    });
  });
});

test.describe('Support', () => {
  test.beforeEach(() => {
    expect(SUPPORT.password, 'Set E2E_SUPPORT_PASSWORD to the SUPPORT_PASSWORD in your .env').not.toBe('');
  });

  test('support sees the read-only account viewer and can suspend and reactivate an account', async ({ page }) => {
    await page.goto('/login');
    await submitLogin(page, SUPPORT.email, SUPPORT.password);

    await expect(page).toHaveURL(/\/portfolio$/);
    await expect(page.getByRole('heading', { name: 'Account viewer' })).toBeVisible();
    await expect(page.getByRole('combobox', { name: 'Account' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Place Order' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Analytics' })).toHaveCount(0);

    // The first account in the list may be one an earlier run left suspended: set it from either side
    const toggle = page.getByRole('switch', { name: 'Account active' });
    await expect(toggle).toBeEnabled();
    const wasActive = (await toggle.getAttribute('aria-checked')) === 'true';
    await toggle.click();
    await expect(toggle).toHaveAttribute('aria-checked', String(!wasActive));
    await toggle.click();
    await expect(toggle).toHaveAttribute('aria-checked', String(wasActive));
  });

  test('support is turned away by the analytics dashboard', async ({ page }) => {
    await page.goto('/analytics/');
    await signInToAnalytics(page, SUPPORT);

    await expect(page.getByText('this dashboard is for admins only')).toBeVisible({ timeout: 30_000 });
    await expect(page.getByRole('heading', { name: 'Live trading' })).toHaveCount(0);
  });
});
