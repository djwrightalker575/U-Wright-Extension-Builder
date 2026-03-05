import { expect, test } from '@playwright/test';

test('template export flow and harness run', async ({ page }) => {
  await page.goto('/');
  await page.getByText('Create Project').click();
  await page.getByText('Project').click();
  await page.getByText('Macro Automator').click();
  await page.getByText('Builder').click();
  await page.getByText('Run').first().click();
  await expect(page.frameLocator('iframe[title="harness"]').locator('#nameInput')).toHaveValue('Foundry');
  await page.getByText('Export').click();
  const dl = page.waitForEvent('download');
  await page.getByText('Export ZIP').click();
  const download = await dl;
  expect(download.suggestedFilename()).toContain('.zip');
});
