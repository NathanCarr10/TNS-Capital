import { expect, test } from '@playwright/test';

import { signInNewTrader } from './support';

test.describe('Settings', () => {
  test('a trader sees their account details and renames the holder', async ({ page, request }) => {
    const trader = await signInNewTrader(page, request);

    await page.getByRole('link', { name: 'Settings' }).click();
    await expect(page).toHaveURL(/\/settings$/);
    const details = page.locator('.details');
    await expect(details).toContainText(trader.holderName);
    await expect(details).toContainText(trader.username);
    await expect(details).toContainText('ACTIVE');
    await expect(details.locator('.opened-on')).not.toBeEmpty();

    const newName = `Renamed ${trader.holderName}`;
    await page.getByRole('button', { name: 'Edit' }).click();
    await page.getByLabel('Account holder name').fill(newName);
    await page.getByRole('button', { name: 'Save' }).click();

    await expect(page.locator('.toast.success')).toContainText('Account holder name updated.');
    await expect(details.locator('.holder-name')).toHaveText(newName);
    // The top bar shows the new name too, and it is kept after a reload
    await expect(page.locator('.user-name')).toHaveText(newName);
    await page.reload();
    await expect(page.locator('.holder-name')).toHaveText(newName);
  });
});
