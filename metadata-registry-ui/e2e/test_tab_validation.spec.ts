import { test, expect } from '@playwright/test';

test('test form tab validation and tab error badge indicators', async ({ page }) => {
  page.on('console', msg => {
    console.log(`[BROWSER CONSOLE] [${msg.type()}] ${msg.text()}`);
  });
  page.on('pageerror', err => {
    console.log('BROWSER EXCEPTION:', err.stack || err.message);
  });

  // Mock API routes
  await page.route('**/api/v1/**', async (route) => {
    const url = route.request().url();
    const method = route.request().method();

    if (url.includes('/api/v1/init-wizard/status')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 0, data: { initialized: true, runningMode: 'SINGLE' } }),
      });
    } else if (url.includes('/api/v1/auth/login')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          data: {
            token: 'mock-admin-token',
            user: { id: 'admin-id', username: 'admin', sysRole: 'SUPER_ADMIN', isRegistrar: true, enabled: true },
          },
        }),
      });
    } else if (url.includes('/api/v1/auth/me')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          data: { id: 'admin-id', username: 'admin', sysRole: 'SUPER_ADMIN', isRegistrar: true, enabled: true },
        }),
      });
    } else if (url.includes('/api/v1/namespaces')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          data: { content: [{ id: 'ns-001', identifier: 'TEST-NS', name: 'Test Namespace' }] },
        }),
      });
    } else if (url.includes('/api/v1/registration-authorities')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          data: { content: [{ id: 'ra-001', identifier: 'TEST-RA', name: 'Test RA' }] },
        }),
      });
    } else if (url.includes('/api/v1/languages')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          data: { content: [{ id: 'zh-CN', languageIdentifier: 'zh-CN' }, { id: 'en', languageIdentifier: 'en' }] },
        }),
      });
    } else if (url.includes('/api/v1/contexts/options')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          data: [
            { id: 'ctx-001', label: 'Test Context' }
          ]
        }),
      });
    } else if (url.includes('/api/v1/data-element-concepts')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          data: { content: [{ id: 'dec-001', label: 'Test DEC' }] },
        }),
      });
    } else if (url.includes('/api/v1/value-domains')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          data: { content: [{ id: 'vd-001', label: 'Test VD' }] },
        }),
      });
    } else if (url.includes('/api/v1/data-elements')) {
      if (method === 'POST') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ code: 0, data: { id: 'de-new' } }),
        });
      } else {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ code: 0, data: { content: [], totalElements: 0, number: 0, size: 20 } }),
        });
      }
    } else {
      await route.fallback();
    }
  });

  // Navigate to login
  await page.goto('/login');
  await page.fill('input[placeholder*="用户名"]', 'admin');
  await page.fill('input[placeholder*="密码"]', 'Admin@123');
  await page.click('button:has-text("登录")');
  await page.waitForURL('**/dashboard');
  console.log('Logged in successfully!');

  // Navigate to /data-elements
  await page.goto('/data-elements');
  
  // Click "新建" button
  await page.click('button:has-text("新建")');
  
  // Wait for Dialog to be visible
  await page.waitForSelector('.el-dialog');

  // Currently active tab should be "注册标识" (registration) by default.
  // We leave the registration fields empty, and switch to "指称" tab.
  await page.click('.el-tabs__item:has-text("指称")');

  // Fill in "指称" to make designations valid
  await page.click('button:has-text("添加指称")');
  await page.waitForTimeout(300);
  await page.locator('#pane-designations .multilingual-card >> nth=0 >> .el-select').filter({ hasText: '语言' }).click();
  await page.waitForTimeout(300);
  await page.click('.el-select-dropdown__item:visible >> nth=0');
  await page.fill('#pane-designations .multilingual-card >> nth=0 >> input[placeholder*="内容"]', 'TabValidationTest');

  // Switch to "属性" tab
  await page.click('.el-tabs__item:has-text("属性")');

  // Fill in fields to make properties tab valid
  await page.locator('#pane-fields .el-select').filter({ hasText: '选择数据元概念' }).click();
  await page.waitForTimeout(300);
  await page.click('.el-select-dropdown__item:visible >> nth=0');

  await page.locator('#pane-fields .el-select').filter({ hasText: '选择值域' }).click();
  await page.waitForTimeout(300);
  await page.click('.el-select-dropdown__item:visible >> nth=0');

  // Now, the "属性" (fields) tab is valid, "指称" (designations) is valid, but "注册标识" (registration) is invalid.
  // We are currently on the "属性" (fields) tab.
  // Let's click "保存".
  console.log('Clicking Save with invalid registration fields...');
  await page.click('.el-dialog__footer >> button:has-text("保存")');

  // Verify that it switched back to the "注册标识" tab automatically
  const activeTabItem = page.locator('.el-tabs__item.is-active');
  await expect(activeTabItem).toContainText('注册标识');

  // Verify that the "注册标识" tab header displays the warning icon/badge
  const warningIcon = page.locator('.el-tabs__item:has-text("注册标识") .tab-error-icon');
  await expect(warningIcon).toBeVisible();

  // Verify that the global warning message appeared
  const messageElement = page.locator('.el-message--warning');
  await expect(messageElement).toBeVisible();
  await expect(messageElement).toContainText('表单验证失败');

  console.log('All E2E checks passed successfully!');
  await page.waitForTimeout(1000);
});
