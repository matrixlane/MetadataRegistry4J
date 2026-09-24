import { test, expect, Page } from '@playwright/test';

/**
 * 版本感知初始化向导 E2E。
 * 覆盖：版本选择卡片、数据库配置步骤（保持当前数据源）、
 * 个人版治理路径（预填默认值）、命名空间步骤、团队版完整治理路径、
 * commit 后跳转登录页。
 */

function mockWizardApis(page: Page, opts: { currentVendor: string; expectEdition: string; raName?: string }) {
  let initialized = false;

  page.route('**/api/v1/init-wizard/status', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 0,
        data: {
          initialized,
          runningMode: 'SINGLE',
          edition: initialized ? opts.expectEdition : null,
        },
      }),
    });
  });

  page.route('**/api/v1/init-wizard/wizard-state', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 0, data: 'READY' }),
    });
  });

  page.route('**/api/v1/datasource/config', async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          data: { vendor: opts.currentVendor, jdbcUrl: 'jdbc:mock://current', schema: null, driverClassName: 'mock.Driver' },
        }),
      });
    } else {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 0, data: null }) });
    }
  });

  page.route('**/api/v1/init-wizard/setup', async (route) => {
    const postData = route.request().postDataJSON(); expect(postData.edition).toBe(opts.expectEdition);
    if (opts.raName) {
      expect(postData.registrationAuthorityName).toBe(opts.raName);
    }
    // 标识与版本生成策略归属于命名空间步骤，默认值随请求提交
    expect(postData.identifierStrategy).toBe('SEGMENT_TEMPLATE');
    expect(postData.versionPolicy).toBe('SEMVER');
    // 注册规范步骤以默认值预填的属性随 setup 提交
    expect(postData.registrySpecificationAttributes.characterRepertoire).toBe('UTF-8');
    expect(postData.registrySpecificationAttributes.standard).toBe('ISO/IEC 11179-3:2023');
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 0,
        message: 'OK',
        data: {
          token: 'mock-wizard-token',
          expiresInSeconds: 3600,
          user: { id: 'user-001', username: 'admin', sysRole: 'SUPER_ADMIN', isRegistrar: true, enabled: true },
        },
      }),
    });
  });

  page.route('**/api/v1/init-wizard/commit', async (route) => {
    const postData = route.request().postDataJSON();
    expect(postData.newPassword).toBe('E2ePass@123');
    expect(postData.confirmPassword).toBe('E2ePass@123');
    initialized = true;
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 0, message: 'OK', data: null }),
    });
  });

  page.route('**/api/v1/auth/me', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 0,
        data: { id: 'user-001', username: 'admin', sysRole: 'SUPER_ADMIN', isRegistrar: true, enabled: true },
      }),
    });
  });

  page.route('**/api/v1/dashboard/stats', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 0, data: {} }) });
  });
}

test('个人版初始化流程（版本 → 数据库 → 治理配置预填默认值 → 密码）', async ({ page }) => {
  mockWizardApis(page, { currentVendor: 'sqlite', expectEdition: 'PERSONAL', raName: '注册管理员' });

  await page.goto('/init-wizard');

  // 版本选择：两张版本卡片可见，默认选中个人版
  const stepTitle = page.locator('.step-title');
  await expect(stepTitle).toContainText('选择部署版本');
  await expect(page.locator('.mode-card.active')).toContainText('个人版');
  await expect(page.locator('.mode-card', { hasText: '团队版' })).toBeVisible();

  await page.click('button:has-text("下一步")');

  // 数据库步骤：显示当前 SQLite 数据源，保持并继续
  await expect(stepTitle).toContainText('配置数据库');
  await expect(page.locator('.db-current-card')).toContainText('sqlite');
  await page.click('button:has-text("下一步")');

  // 个人版同样展示治理步骤，治理字段预填默认值
  await expect(stepTitle).toContainText('配置默认注册机构');
  await expect(page.locator('input[placeholder*="信息化管理部"]')).toHaveValue('注册管理员');
  await page.click('button:has-text("下一步")');

  // 命名空间：独立的策略配置步骤（标识与版本输入模式各渲染一组相同的单选按钮）
  await expect(stepTitle).toContainText('配置命名空间');
  await expect(page.locator('.el-radio-button', { hasText: '自动生成（允许手动编辑）' })).toHaveCount(2);
  await expect(page.locator('.el-select', { hasText: '语义化版本' })).toBeVisible();
  await page.click('button:has-text("下一步")');

  // 注册规范：完整属性表单，系统默认值预填（名称随注册机构联动，时区 +0800）
  await expect(stepTitle).toContainText('配置注册规范');
  await expect(page.locator('.wizard-form input').nth(0)).toHaveValue('注册管理员元数据注册表规范');
  await expect(page.locator('.wizard-form input[placeholder*="HHMM"]')).toHaveValue('+0800');
  await page.click('button:has-text("下一步")');

  await expect(stepTitle).toContainText('设置超级管理员密码');
  await page.fill('input[placeholder="6-20 个字符"]', 'E2ePass@123');
  await page.fill('input[placeholder="再次输入新密码"]', 'E2ePass@123');

  await page.click('button:has-text("完成初始化并启用系统")');

  // commit 成功后跳转登录页（向导会话令牌已废弃，需用新密码登录）
  await page.waitForURL('**/login');
  expect(page.url()).toContain('/login');
});

test('团队版完整初始化流程（版本 → 数据库 → 治理配置 → 密码）', async ({ page }) => {
  mockWizardApis(page, { currentVendor: 'sqlserver', expectEdition: 'TEAM', raName: 'E2E Team RA' });

  await page.goto('/init-wizard');

  const stepTitle = page.locator('.step-title');
  await expect(stepTitle).toContainText('选择部署版本');

  // 选择团队版
  await page.locator('.mode-card', { hasText: '团队版' }).click();
  await expect(page.locator('.mode-card.active')).toContainText('团队版');
  await page.click('button:has-text("下一步")');

  // 数据库步骤：当前为 SQL Server，团队版允许继续
  await expect(stepTitle).toContainText('配置数据库');
  await expect(page.locator('.db-current-card')).toContainText('sqlserver');
  await page.click('button:has-text("下一步")');

  // 注册机构
  await expect(stepTitle).toContainText('配置默认注册机构');
  await page.fill('input[placeholder*="信息化管理部"]', 'E2E Team RA');
  await page.click('button:has-text("下一步")');

  // 命名空间
  await expect(stepTitle).toContainText('配置命名空间');
  await page.click('button:has-text("下一步")');

  // 注册规范：默认值预填，直接继续
  await expect(stepTitle).toContainText('配置注册规范');
  await page.click('button:has-text("下一步")');

  // 管理员密码
  await expect(stepTitle).toContainText('设置超级管理员密码');
  await page.fill('input[placeholder="6-20 个字符"]', 'E2ePass@123');
  await page.fill('input[placeholder="再次输入新密码"]', 'E2ePass@123');
  await page.click('button:has-text("完成初始化并启用系统")');

  await page.waitForURL('**/login');
  expect(page.url()).toContain('/login');
});

test('团队版使用 SQLite 时被数据库步骤阻止', async ({ page }) => {
  mockWizardApis(page, { currentVendor: 'sqlite', expectEdition: 'TEAM' });

  await page.goto('/init-wizard');
  const stepTitle = page.locator('.step-title');

  await page.locator('.mode-card', { hasText: '团队版' }).click();
  await page.click('button:has-text("下一步")');

  // 数据库步骤：当前为 SQLite，团队版继续应被阻止并提示
  await expect(stepTitle).toContainText('配置数据库');
  await page.click('button:has-text("下一步")');
  await expect(page.locator('.el-message')).toContainText('团队版不支持 SQLite');
  // 仍停留在数据库步骤
  await expect(stepTitle).toContainText('配置数据库');
});
