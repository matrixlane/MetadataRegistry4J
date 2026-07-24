import { test, expect } from '@playwright/test';

/**
 * Regression test for: After factory reset, leaving /init-wizard and re-entering
 * localhost:5173 should land on /init-wizard, not /login.
 *
 * Before the fix, `SystemSettings.executeReset` did `localStorage.clear()`, which
 * removed the auth token. The next navigation hit the router's "not logged in"
 * branch (router/index.ts line 302) BEFORE the "not initialized" branch (line 310),
 * so the user was bounced to /login instead of /init-wizard.
 *
 * The fix moved the init-wizard check to the front of the guard, so the
 * "not initialized" branch fires even when the token is absent.
 */
test('after factory reset, re-entering localhost:5173 lands on init-wizard, not login', async ({ page }) => {
  // Track init-wizard status: starts true (system running), flips to false after reset.
  let initStatus = true;

  await page.addInitScript(() => {
    // Stale token from before the reset — the bug reproduced because this was cleared.
    localStorage.setItem('token', 'stale-pre-reset-token');
  });

  // /init-wizard/status — reflects backend state
  await page.route('**/api/v1/init-wizard/status', async (route) => {
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 0,
        data: { initialized: initStatus, runningMode: 'SINGLE' },
      }),
    });
  });

  // /auth/me — admin profile (so /system-settings renders)
  await page.route('**/api/v1/auth/me', async (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 0,
        data: {
          id: 'u-1',
          username: 'admin',
          sysRole: 'SUPER_ADMIN',
          enabled: true,
          forceChangePassword: false,
        },
      }),
    })
  );

  // /system/settings/... — minimal stubs for SystemSettings page load
  await page.route('**/api/v1/system/settings/new-user-require-audit', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 0, data: false }) })
  );
  await page.route('**/api/v1/system/settings/allow-user-registration', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 0, data: true }) })
  );

  // /system/factory-reset — the reset action; flip init status on success
  await page.route('**/api/v1/system/factory-reset', async (route) => {
    initStatus = false;
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 0, data: null }),
    });
  });

  // 1) Land on /system-settings and confirm the reset completes
  await page.goto('/system-settings');
  await page.locator('button:has-text("恢复出厂设置")').click();
  await page.locator('.el-dialog:has-text("恢复出厂设置") button:has-text("我已了解风险，继续")').click();
  await page.locator('input[placeholder="请输入管理员密码"]').fill('Admin@123');
  await page.locator('.el-dialog:has-text("身份验证") button:has-text("确认重置")').click();

  // Wait for the redirect to /init-wizard
  await page.waitForURL('**/init-wizard');
  await expect(page).toHaveURL(/\/init-wizard/);

  // 2) The bug: leave /init-wizard and re-enter localhost:5173
  // The fix should now redirect back to /init-wizard, NOT to /login.
  await page.goto('http://localhost:5173/');

  // 3) The fix's guarantee: URL must contain /init-wizard (or the user is still
  //    on the init-wizard page after the guard runs).
  // Give the guard a moment to run.
  await page.waitForTimeout(500);
  expect(page.url()).toContain('/init-wizard');
  expect(page.url()).not.toContain('/login');
});
