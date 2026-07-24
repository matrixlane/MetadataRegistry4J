import { test, expect } from '@playwright/test';
import { setupAuthenticatedPage } from './auth.setup';

test('Factory reset workflow E2E', async ({ page }) => {
  page.on('console', msg => console.log('BROWSER CONSOLE:', msg.text()));
  page.on('pageerror', err => console.log('BROWSER EXCEPTION:', err.stack || err.message));

  await setupAuthenticatedPage(page);

  let resetCalled = false;
  let initStatus = true;

  // Intercept the factory-reset API endpoint
  await page.route('**/api/v1/system/factory-reset', async (route) => {
    const postData = route.request().postDataJSON();
    expect(postData.password).toBe('Admin@123');
    resetCalled = true;
    initStatus = false; // Mark as uninitialized now

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 0,
        message: 'OK',
        data: null
      })
    });
  });

  // Intercept system settings query
  await page.route('**/api/v1/system/settings/new-user-require-audit', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 0,
        data: false
      })
    });
  });

  // Intercept allow-user-registration setting query
  await page.route('**/api/v1/system/settings/allow-user-registration', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 0,
        data: true
      })
    });
  });

  // Intercept init-wizard status endpoint
  await page.route('**/api/v1/init-wizard/status', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 0,
        data: {
          initialized: initStatus,
          runningMode: 'SINGLE'
        }
      })
    });
  });

  console.log('Navigating to system settings...');
  await page.goto('/system-settings');

  // Verify page title
  const pageTitle = page.locator('h2');
  await expect(pageTitle).toContainText('系统设置');

  // Click on the danger zone button
  const resetButton = page.locator('.danger-zone-item button:has-text("恢复出厂设置")');
  await expect(resetButton).toBeVisible();
  await resetButton.click();

  // Step 1: Risk warning dialog
  const confirmDialog = page.locator('.el-dialog:has-text("恢复出厂设置")');
  await expect(confirmDialog).toBeVisible();
  await expect(confirmDialog).toContainText('此操作将永久清除以下所有数据');

  // Click continue risk warning
  const continueRiskButton = confirmDialog.locator('button:has-text("我已了解风险，继续")');
  await expect(continueRiskButton).toBeVisible();
  await continueRiskButton.click();

  // Step 2: Password verification dialog
  const passwordDialog = page.locator('.el-dialog:has-text("身份验证")');
  await expect(passwordDialog).toBeVisible();
  await expect(passwordDialog).toContainText('请输入您当前的登录密码以确认此操作');

  // Fill in password
  const passwordInput = passwordDialog.locator('input[placeholder="请输入管理员密码"]');
  await expect(passwordInput).toBeVisible();
  await passwordInput.fill('Admin@123');

  // Click confirm reset
  const confirmResetButton = passwordDialog.locator('button:has-text("确认重置")');
  await expect(confirmResetButton).toBeVisible();
  await confirmResetButton.click();

  // Assert overlay is displayed
  const overlay = page.locator('.reset-overlay');
  await expect(overlay).toBeVisible();
  await expect(overlay).toContainText('正在恢复出厂设置…');

  // Verify it redirects back to init-wizard after successful reset
  await page.waitForURL('**/init-wizard');
  expect(page.url()).toContain('/init-wizard');
  expect(resetCalled).toBe(true);

  console.log('Factory reset E2E test passed successfully!');
});
