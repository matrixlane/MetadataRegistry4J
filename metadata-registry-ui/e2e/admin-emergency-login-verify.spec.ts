import { test, expect } from '@playwright/test';

/**
 * Verification for tasks 4.2 / 4.4 in admin-emergency-login:
 *  - Login page hides the "注册" link when /auth/allow-registration returns false
 *  - Login page shows the "注册" link when /auth/allow-registration returns true
 *  - SystemSettings page toggles the "允许注册新用户" switch and persists via API
 *  - AppUserList shows the "上次登录时间" column, disables self-row 删除/禁用 buttons,
 *    and exposes a "重置密码" button on regular-user rows that calls the reset API
 *    and shows the one-shot temporary password.
 *
 * Strategy: the dev server is started by Playwright's webServer config, but the
 * Spring backend is not available in this environment. We mock every relevant
 * /api/v1/* request with the response shapes that the frontend actually consumes.
 */

// ---------- shared response helpers ----------

const ok = <T>(data: T) => ({ code: 0, data, message: 'success' });

const sampleUsers = [
  {
    id: 'u-1',
    username: 'admin',
    sysRole: 'SUPER_ADMIN',
    enabled: true,
    createdAt: '2026-01-01T00:00:00Z',
    lastLoginAt: '2026-06-07T10:00:00Z',
  },
  {
    id: 'u-2',
    username: 'alice',
    sysRole: null,
    enabled: true,
    createdAt: '2026-01-15T00:00:00Z',
    lastLoginAt: '2026-06-05T09:00:00Z',
  },
  {
    id: 'u-3',
    username: 'bob',
    sysRole: null,
    enabled: false,
    createdAt: '2026-02-01T00:00:00Z',
    lastLoginAt: null,
  },
];

const adminProfile = {
  id: 'u-1',
  username: 'admin',
  sysRole: 'SUPER_ADMIN',
  enabled: true,
  forceChangePassword: false,
};

// ---------- 4.2a: Login page hides register link when switch is OFF ----------

test('login page hides register link when allow-registration is false', async ({ page }) => {
  // System is initialized (otherwise the new init guard redirects to /init-wizard first)
  await page.route('**/api/v1/init-wizard/status', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(ok({ initialized: true, runningMode: 'SINGLE' })),
    })
  );
  await page.route('**/api/v1/auth/allow-registration', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(ok(false)),
    })
  );

  await page.goto('/login');
  await expect(page.locator('h2')).toContainText(/MetaDataRegistry|元数据|注册/);

  // The register link text is "新用户注册" in Login.vue
  await expect(page.locator('text=新用户注册')).toHaveCount(0);
});

test('login page shows register link when allow-registration is true', async ({ page }) => {
  await page.route('**/api/v1/init-wizard/status', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(ok({ initialized: true, runningMode: 'SINGLE' })),
    })
  );
  await page.route('**/api/v1/auth/allow-registration', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(ok(true)),
    })
  );

  await page.goto('/login');
  await expect(page.locator('text=新用户注册')).toBeVisible();
});

// ---------- 4.2b: SystemSettings toggles the switch and calls the API ----------

test('system settings toggles allow-user-registration and persists via API', async ({ page }) => {
  // Inject a valid-looking token so the router guard treats us as logged in.
  await page.addInitScript(() => {
    localStorage.setItem('token', 'test-token');
  });

  // Auth profile (admin) so the page renders
  await page.route('**/api/v1/auth/me', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(ok(adminProfile)) })
  );

  // System is initialized (this is what the new init guard checks first)
  await page.route('**/api/v1/init-wizard/status', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(ok({ initialized: true, runningMode: 'SINGLE' })),
    })
  );

  // Initial switch state: ON
  await page.route('**/api/v1/system/settings/allow-user-registration**', async (route) => {
    if (route.request().method() === 'GET') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(ok(true)),
      });
    }
    // PUT — capture the new value and acknowledge
    const url = new URL(route.request().url());
    const allow = url.searchParams.get('allowRegistration');
    (globalThis as any).__lastAllowRegistration = allow;
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(ok(null)),
    });
  });

  await page.route('**/api/v1/system/settings/new-user-require-audit', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(ok(false)),
    })
  );

  await page.goto('/system-settings');

  // Wait for the switch to render and reflect the initial true state
  const allowSwitch = page.locator('text=允许注册新用户').locator('..').locator('..').locator('.el-switch');
  await expect(allowSwitch).toBeVisible();

  // Flip it off
  await allowSwitch.click();

  // Wait for the PUT to actually fire
  await expect.poll(async () => (globalThis as any).__lastAllowRegistration, { timeout: 3000 }).toBe('false');
});

// ---------- 4.4a: AppUserList shows lastLoginAt column ----------

test('app user list shows the last login time column', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'test-token');
  });
  await page.route('**/api/v1/auth/me', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(ok(adminProfile)) })
  );
  await page.route('**/api/v1/init-wizard/status', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(ok({ initialized: true, runningMode: 'SINGLE' })),
    })
  );
  await page.route('**/api/v1/accounts*', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(ok({ content: sampleUsers, totalElements: sampleUsers.length })),
    })
  );

  await page.goto('/app-users');
  await expect(page.locator('th:has-text("上次登录时间")')).toBeVisible();

  // alice has a real lastLoginAt; bob is null -> shows "-"
  const aliceRow = page.locator('tr', { has: page.locator('text=alice') });
  await expect(aliceRow).toContainText(/2026/);

  const bobRow = page.locator('tr', { has: page.locator('text=bob') });
  await expect(bobRow).toContainText('-');
});

// ---------- 4.4b: self-row disable buttons + 4.4c reset password flow ----------

test('app user list disables self-row 删除/禁用 and shows reset-password dialog', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'test-token');
  });
  await page.route('**/api/v1/auth/me', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(ok(adminProfile)) })
  );
  await page.route('**/api/v1/init-wizard/status', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(ok({ initialized: true, runningMode: 'SINGLE' })),
    })
  );
  await page.route('**/api/v1/accounts*', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(ok({ content: sampleUsers, totalElements: sampleUsers.length })),
    })
  );

  let resetCalledFor: string | null = null;
  await page.route('**/api/v1/accounts/*/reset-password', (route) => {
    const m = /\/api\/v1\/accounts\/([^/]+)\/reset-password/.exec(route.request().url());
    resetCalledFor = m ? m[1] : null;
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(ok('Tmp#aB12x9')),
    });
  });

  await page.goto('/app-users');
  await expect(page.locator('th:has-text("上次登录时间")')).toBeVisible();

  // 1) self-row 删除 / 禁用 buttons are disabled
  const adminRow = page.locator('tr', { has: page.locator('text=admin') });
  const adminDelete = adminRow.locator('button:has-text("删除")');
  const adminDisable = adminRow.locator('button:has-text("禁用")');
  await expect(adminDelete).toBeDisabled();
  await expect(adminDisable).toBeVisible();
  await expect(adminDisable).toBeDisabled();

  // 2) regular-user row has an enabled 重置密码 button
  const aliceRow = page.locator('tr', { has: page.locator('text=alice') });
  const aliceReset = aliceRow.locator('button:has-text("重置密码")');
  await expect(aliceReset).toBeEnabled();

  // 3) clicking it triggers a confirm, then the API, then the one-shot dialog
  await aliceReset.click();
  const confirmBox = page.locator('.el-message-box').filter({ hasText: '确定要重置用户' });
  await expect(confirmBox).toBeVisible();
  await confirmBox.locator('button:has-text("确定")').click();

  // Wait for the second (result) dialog showing the temporary password
  const resultBox = page.locator('.el-message-box').filter({ hasText: '重置成功' });
  await expect(resultBox).toBeVisible();
  await expect(resultBox).toContainText('Tmp#aB12x9');
  await expect(resultBox).toContainText('此密码仅能成功登录一次');

  expect(resetCalledFor).toBe('u-2'); // alice
});
