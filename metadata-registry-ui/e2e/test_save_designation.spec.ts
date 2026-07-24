import { test, expect } from '@playwright/test';

test('test save data element with designation and context', async ({ page }) => {
  page.on('console', msg => {
    console.log(`[BROWSER CONSOLE] [${msg.type()}] ${msg.text()}`);
  });
  page.on('pageerror', err => {
    console.log('BROWSER EXCEPTION:', err.stack || err.message);
  });

  // Track API requests and responses
  page.on('request', request => {
    if (request.url().includes('/api/')) {
      console.log(`>> REQUEST: ${request.method()} ${request.url()}`);
      if (request.method() === 'POST' || request.method() === 'PUT') {
        console.log(`Payload:`, request.postData());
      }
    }
  });
  page.on('response', response => {
    if (response.url().includes('/api/')) {
      console.log(`<< RESPONSE: ${response.status()} ${response.url()}`);
      response.text().then(text => {
        console.log(`Response Body:`, text);
      }).catch(() => {});
    }
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

  // Fill in Registration Info Tab (which should be active by default if showRegistration is true)
  // Select Namespace
  await page.locator('.el-form-item').filter({ hasText: '命名空间' }).locator('.el-select').click();
  await page.waitForTimeout(500);
  // Pick the first option
  await page.click('.el-select-dropdown__item:visible >> nth=0');

  // Fill Identifier
  const uniqueId = `TEST-DE-${Date.now()}`;
  await page.fill('input[placeholder*="唯一标识符"]', uniqueId);

  // Fill Version
  await page.fill('input[placeholder*="如 1.0"]', '1.0');

  // Select Registration Authority
  await page.locator('.el-form-item').filter({ hasText: '注册机构' }).locator('.el-select').click();
  await page.waitForTimeout(500);
  await page.click('.el-select-dropdown__item:visible >> nth=0');

  // Switch to "指称" tab
  await page.click('.el-tabs__item:has-text("指称")');

  // Click "添加指称" button
  await page.click('button:has-text("添加指称")');
  await page.waitForTimeout(500);

  // Under Designations, select language
  await page.locator('#pane-designations .multilingual-card >> nth=0 >> .el-select').filter({ hasText: '语言' }).click();
  await page.waitForTimeout(500);
  // select first language
  await page.click('.el-select-dropdown__item:visible >> nth=0');

  // fill value
  await page.fill('#pane-designations .multilingual-card >> nth=0 >> input[placeholder*="内容"]', 'TestDesignation');

  // Expand Context Section
  await page.click('#pane-designations .multilingual-card >> nth=0 >> .context-header');
  await page.waitForTimeout(500);

  // Add context relationship
  await page.click('#pane-designations .multilingual-card >> nth=0 >> button:has-text("添加上下文")');

  // Select context
  const contextInput = page.locator('#pane-designations .multilingual-card >> nth=0 >> .context-row >> nth=0 >> .el-select').filter({ hasText: '选择上下文' }).locator('input');
  await contextInput.fill('Test');
  await page.waitForTimeout(500);
  // Select first option
  await page.click('.el-select-dropdown__item:visible >> nth=0');

  // Select acceptability
  await page.locator('#pane-designations .multilingual-card >> nth=0 >> .context-row >> nth=0 >> .el-select >> nth=1').click();
  await page.waitForTimeout(500);
  await page.click('.el-select-dropdown__item:visible:has-text("认可")');

  // Switch to "定义" tab
  await page.click('.el-tabs__item:has-text("定义")');
  await page.waitForTimeout(500);

  // Click "添加定义" button
  await page.click('button:has-text("添加定义")');
  await page.waitForTimeout(500);

  // First row language
  await page.locator('#pane-definitions .multilingual-card >> nth=0 >> .el-select').filter({ hasText: '语言' }).click();
  await page.waitForTimeout(500);
  await page.click('.el-select-dropdown__item:visible >> nth=0');
  // value
  await page.fill('#pane-definitions .multilingual-card >> nth=0 >> input[placeholder*="内容"]', 'Test Definition');

  // Switch to "属性" tab and select Data Element Concept and Value Domain
  await page.click('.el-tabs__item:has-text("属性")');
  
  // Select Data Element Concept
  await page.locator('#pane-fields .el-select').filter({ hasText: '选择数据元概念' }).click();
  await page.waitForTimeout(500);
  await page.click('.el-select-dropdown__item:visible >> nth=0');

  // Select Value Domain
  await page.locator('#pane-fields .el-select').filter({ hasText: '选择值域' }).click();
  await page.waitForTimeout(500);
  await page.click('.el-select-dropdown__item:visible >> nth=0');

  // Click Save
  console.log('Clicking Save...');
  await page.click('.el-dialog__footer >> button:has-text("保存")');

  // Wait 3 seconds to see result/errors
  await page.waitForTimeout(3000);
});
