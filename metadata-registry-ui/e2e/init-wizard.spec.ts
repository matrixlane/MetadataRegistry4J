import { test, expect, Page } from '@playwright/test';

/**
 * 版本感知初始化向导 E2E。
 * 覆盖：版本选择卡片、数据库配置步骤（保持当前数据源）、
 * 个人版治理路径（预填默认值）、命名空间步骤、团队版完整治理路径、
 * commit 后跳转登录页。
 */

function mockWizardApis(page: Page, opts: {
  currentVendor: string;
  expectEdition: string;
  raName?: string;
  /** TEAM 版关闭「设为注册人员」时：注册人员要素留空且不随请求提交 */
  selfAsRegistrarOff?: boolean;
}) {
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

  // 启动等待屏轮询的就绪探针。缺此 mock 时会打到真实后端，后端不在即整页卡在等待屏。
  page.route('**/api/v1/startup/status', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 0, message: 'OK', data: { ready: true } }),
    });
  });

  // 文档语言下拉的候选项来自 /languages（分页响应）。缺此 mock 时真实请求 401，
  // 下拉为空，TEAM 版「至少选择 1 个语言」步骤无法选中任何项。
  page.route('**/api/v1/languages**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 0,
        message: 'OK',
        data: {
          content: [
            { id: 'lang-zh-cn', languageIdentifier: 'zh-CN', languageName: '中文', enabled: true },
            { id: 'lang-en-us', languageIdentifier: 'en-US', languageName: '英语', enabled: true },
          ],
          totalElements: 2,
          size: 200,
        },
      }),
    });
  });

  // 初始化向导 MUST NOT 请求组织列表（初始化阶段不存在任何可选组织）。
  // 保留一条 fail-fast 路由：一旦向导发起该请求即判定回归失败。
  page.route('**/api/v1/organizations/options', async (route) => {
    throw new Error('初始化向导不应请求 /organizations/options');
  });

  page.route('**/api/v1/init-wizard/setup', async (route) => {
    const postData = route.request().postDataJSON(); expect(postData.edition).toBe(opts.expectEdition);
    if (opts.raName) {
      expect(postData.registrationAuthorityName).toBe(opts.raName);
    }
    // ISO/IEC 11179-3:2023 表 24 文档语言随 setup 提交
    if (opts.expectEdition === 'PERSONAL') {
      expect(postData.registrationAuthorityDocumentLanguages).toEqual(['zh-CN']);
    }
    if (opts.expectEdition === 'TEAM') {
      expect(postData.registrationAuthorityDocumentLanguages).toEqual(['en-US']);
    }
    // add-registrar-three-elements: 注册管理员姓名与角色随 setup 提交；
    // 所属组织不由客户端提交（RegistrationAuthority extends Organization，RA 本身即组织）
    expect(postData.adminOrganizationId).toBeUndefined();
    if (opts.expectEdition === 'PERSONAL') {
      // 前端不预填姓名（保持空白 = 选填）；后端对空值不兜底，PERSONAL 初始化不创建 Individual
      expect(postData.adminIndividualName).toBeNull();
      expect(postData.adminRoleName).toBe('注册管理员');
    }
    if (opts.expectEdition === 'TEAM' && opts.selfAsRegistrarOff) {
      // 关闭「设为注册人员」→ 不创建 Registrar → 姓名与角色留空且不再校验
      expect(postData.selfAsRegistrar).toBe(false);
      expect(postData.adminIndividualName).toBeNull();
      expect(postData.adminRoleName).toBeNull();
    } else if (opts.expectEdition === 'TEAM') {
      expect(postData.selfAsRegistrar).toBe(true);
      expect(postData.adminIndividualName).toBe('李雷');
      expect(postData.adminRoleName).toBe('数据治理主管');
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
  mockWizardApis(page, { currentVendor: 'sqlite', expectEdition: 'PERSONAL', raName: '默认注册机构' });

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
  await expect(page.locator('input[placeholder*="信息化管理部"]')).toHaveValue('默认注册机构');
  await page.click('button:has-text("下一步")');

  // 命名空间：独立的策略配置步骤（标识与版本输入模式各渲染一组相同的单选按钮）
  await expect(stepTitle).toContainText('配置命名空间');
  await expect(page.locator('.el-radio-button', { hasText: '自动生成（允许手动编辑）' })).toHaveCount(2);
  await expect(page.locator('.el-select', { hasText: '语义化版本' })).toBeVisible();
  await page.click('button:has-text("下一步")');

  // 注册规范：完整属性表单，系统默认值预填（名称为稳定默认值，时区 +0800）
  await expect(stepTitle).toContainText('配置注册规范');
  await expect(page.locator('.wizard-form input').nth(0)).toHaveValue('默认注册规范');
  await expect(page.locator('.wizard-form input[placeholder*="HHMM"]')).toHaveValue('+0800');
  await page.click('button:has-text("下一步")');

  await expect(stepTitle).toContainText('设置超级管理员密码');
  await page.fill('input[placeholder="8-64 个字符"]', 'E2ePass@123');
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
  // ISO/IEC 11179-3:2023 表 24 文档语言（团队版必填，至少 1 个）
  // 选项 label 为语言中文名，value 为 BCP 47 标识符
  await page.locator('.el-select').filter({ hasText: '至少选择 1 个语言' }).click();
  await page.locator('.el-select-dropdown__item', { hasText: '英语' }).first().click();
  await page.keyboard.press('Escape');
  // 名称
  await page.fill('input[placeholder*="信息化管理部"]', 'E2E Team RA');
  // add-registrar-three-elements: 显式填写姓名与角色（按表单项标签定位，不依赖 placeholder 文案）
  await page.locator('.el-form-item').filter({ hasText: '注册人员姓名' }).locator('input').fill('李雷');
  await page.locator('.el-form-item').filter({ hasText: '注册人员角色' }).locator('input').fill('数据治理主管');
  // 所属组织不提供选择控件（初始化阶段不存在任何可选组织）
  await expect(page.locator('.el-select').filter({ hasText: '选择组织' })).toHaveCount(0);
  await page.click('button:has-text("下一步")');

  // 命名空间
  await expect(stepTitle).toContainText('配置命名空间');
  await page.click('button:has-text("下一步")');

  // 注册规范：默认值预填，直接继续
  await expect(stepTitle).toContainText('配置注册规范');
  await page.click('button:has-text("下一步")');

  // 管理员密码
  await expect(stepTitle).toContainText('设置超级管理员密码');
  await page.fill('input[placeholder="8-64 个字符"]', 'E2ePass@123');
  await page.fill('input[placeholder="再次输入新密码"]', 'E2ePass@123');
  await page.click('button:has-text("完成初始化并启用系统")');

  await page.waitForURL('**/login');
  expect(page.url()).toContain('/login');
});

test('团队版关闭「设为注册人员」后要素留空仍可完成注册机构步骤', async ({ page }) => {
  mockWizardApis(page, {
    currentVendor: 'sqlserver',
    expectEdition: 'TEAM',
    raName: 'E2E Team RA NoRegistrar',
    selfAsRegistrarOff: true,
  });

  await page.goto('/init-wizard');
  const stepTitle = page.locator('.step-title');

  await page.locator('.mode-card', { hasText: '团队版' }).click();
  await page.click('button:has-text("下一步")');
  await expect(stepTitle).toContainText('配置数据库');
  await page.click('button:has-text("下一步")');

  // 注册机构步骤
  await expect(stepTitle).toContainText('配置默认注册机构');
  await page.locator('.el-select').filter({ hasText: '至少选择 1 个语言' }).click();
  await page.locator('.el-select-dropdown__item', { hasText: '英语' }).first().click();
  await page.keyboard.press('Escape');
  await page.fill('input[placeholder*="信息化管理部"]', 'E2E Team RA NoRegistrar');

  // 关闭「将当前超级管理员设为该机构的注册人员」
  await expect(page.locator('.el-switch')).toBeVisible();
  await page.locator('.el-switch').click();

  // 姓名与角色均留空——不创建 Registrar 时不应被要求填写，直接进入下一步
  const nameInput = page.locator('.el-form-item').filter({ hasText: '注册人员姓名' }).locator('input');
  const roleInput = page.locator('.el-form-item').filter({ hasText: '注册人员角色' }).locator('input');
  await expect(nameInput).toHaveValue('');
  await expect(roleInput).toHaveValue('');

  await page.click('button:has-text("下一步")');
  await expect(stepTitle).toContainText('配置命名空间');

  // 继续走完流程，触发 setup payload 断言：selfAsRegistrar=false 且两个要素均为 null
  await page.click('button:has-text("下一步")');
  await expect(stepTitle).toContainText('配置注册规范');
  await page.click('button:has-text("下一步")');

  await expect(stepTitle).toContainText('设置超级管理员密码');
  await page.fill('input[placeholder="8-64 个字符"]', 'E2ePass@123');
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
