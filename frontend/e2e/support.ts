import { APIRequestContext, Page, expect } from '@playwright/test';

/** The seeded admin (db/data/06_users_seed.sql). */
export const ADMIN = { username: 'admin', password: 'adminPassword' };

export interface Trader {
  username: string;
  password: string;
  holderName: string;
}

/** A trader nobody has registered yet, so every run starts from an empty account. */
export function newTrader(): Trader {
  const suffix = `${Date.now()}${Math.floor(Math.random() * 1000)}`;
  return { username: `e2e${suffix}`, password: 'e2e-password-1', holderName: `E2E Trader ${suffix}` };
}

/** Registers through the auth API and returns the new trading account's id. */
export async function registerViaApi(request: APIRequestContext, trader: Trader): Promise<number> {
  const response = await request.post('/auth/register', { data: trader });
  expect(response.status(), await response.text()).toBe(201);
  return (await response.json()).accountId;
}

/**
 * Funds an account through the trading API. New accounts open with a zero balance and the
 * UI has no deposit form yet, so a BUY needs this first.
 */
export async function depositViaApi(
  request: APIRequestContext,
  trader: Trader,
  accountId: number,
  amount: number
): Promise<void> {
  const login = await request.post('/auth/login', {
    data: { username: trader.username, password: trader.password },
  });
  expect(login.ok(), await login.text()).toBeTruthy();
  const { accessToken } = await login.json();

  const deposit = await request.post(`/api/v1/accounts/${accountId}/deposit`, {
    data: { amount },
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  expect(deposit.ok(), await deposit.text()).toBeTruthy();
}

/** Fills and submits the Angular login form (the page must already be on /login). */
export async function submitLogin(page: Page, username: string, password: string): Promise<void> {
  await page.getByLabel('Username').fill(username);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Login' }).click();
}
