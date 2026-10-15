import { APIRequestContext, Locator, Page, expect } from '@playwright/test';

/**
 * The admin, which the auth service creates from ADMIN_PASSWORD: ci/e2e-tests.sh generates one
 * and passes it in; against your own stack, set E2E_ADMIN_PASSWORD to the one in your .env.
 */
export const ADMIN = { username: 'admin', password: process.env['E2E_ADMIN_PASSWORD'] ?? '' };

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

/** Fills and submits the Angular login form (the page must already be on /login). */
export async function submitLogin(page: Page, username: string, password: string): Promise<void> {
  await page.getByLabel('Username').fill(username);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Login' }).click();
}

/** Deposits cash with the portfolio page's Deposit cash form (the page must be on /portfolio). */
export async function depositViaUi(page: Page, amount: number): Promise<void> {
  await page.getByRole('button', { name: 'Deposit cash' }).click();
  const form = page.locator('form.deposit-form');
  await form.getByLabel('Amount').fill(String(amount));
  await form.getByRole('button', { name: 'Confirm deposit' }).click();
  await expect(form.getByRole('status')).toHaveText('Cash deposited successfully.');
}

/**
 * Submits a limit order from /trade/:symbol and returns the ticket. Each order carries its own
 * limit price, so the tests don't depend on market data being configured.
 */
export async function submitOrder(
  page: Page,
  order: { side: 'BUY' | 'SELL'; symbol: string; quantity: number; price: number }
): Promise<Locator> {
  await page.goto(`/trade/${order.symbol}`);
  const ticket = page.locator('form.ticket-body');
  await ticket.getByRole('radio', { name: order.side }).click();
  await expect(ticket.getByRole('radio', { name: order.side })).toHaveAttribute('aria-checked', 'true');
  await ticket.getByLabel('Quantity').fill(String(order.quantity));
  await ticket.getByLabel('Limit price').fill(String(order.price));
  const verb = order.side === 'BUY' ? 'Buy' : 'Sell';
  await ticket.getByRole('button', { name: `${verb} ${order.quantity} ${order.symbol}` }).click();
  return ticket;
}

/** A new trader, signed in on /portfolio. */
export async function signInNewTrader(page: Page, request: APIRequestContext): Promise<Trader> {
  const trader = newTrader();
  await registerViaApi(request, trader);
  await page.goto('/login');
  await submitLogin(page, trader.username, trader.password);
  await expect(page).toHaveURL(/\/portfolio$/);
  return trader;
}
