import { test, expect, type Page, type Locator } from '@playwright/test';
import { setupAuthenticatedPage } from './auth.setup';

test.describe('Sidebar Menu Reorganization', () => {
  test.beforeEach(async ({ page }) => {
    await setupAuthenticatedPage(page);

    // Catch-all mock for other API endpoints to prevent 401 redirects when page initializes
    await page.route('**/api/v1/**', async (route) => {
      const url = route.request().url();

      // Handle backend startup readiness probe (gates app mount in main.ts)
      if (url.includes('/api/v1/startup/status')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 0,
            data: { ready: true },
          }),
        });
        return;
      }

      // Handle auth/me check
      if (url.includes('/api/v1/auth/me')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 0,
            data: {
              id: 'user-001',
              username: 'testuser',
              sysRole: 'SUPER_ADMIN',
              isRegistrar: true,
              enabled: true,
            },
          }),
        });
        return;
      }

      // Handle init-wizard status check
      if (url.includes('/api/v1/init-wizard/status')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 0,
            data: {
              initialized: true,
              runningMode: 'SINGLE',
            },
          }),
        });
        return;
      }

      // Default empty list response for any other API queries
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          data: {
            content: [],
            totalElements: 0,
            number: 0,
            size: 20,
          },
        }),
      });
    });
  });

  function mainLabel(scope: Page | Locator, text: string): Locator {
    return scope.locator('.menu-label__main').filter({ hasText: new RegExp(`^${text}$`) });
  }

  async function expandGroup(page: Page, groupTitle: string): Promise<Locator> {
    const group = page.locator('.el-sub-menu').filter({
      has: page.locator('> .el-sub-menu__title > .menu-label > .menu-label__main', {
        hasText: new RegExp(`^${groupTitle}$`),
      }),
    });
    // 幂等展开：目标路由激活时 el-menu 会自动展开所属子菜单，
    // 此时再点标题反而会将其折叠
    const submenu = group.locator('> ul.el-menu');
    if (!(await submenu.isVisible())) {
      await group.locator('> .el-sub-menu__title').click();
    }
    return group;
  }

  async function expectGroupItems(page: Page, groupTitle: string, labels: string[]) {
    const group = await expandGroup(page, groupTitle);
    const items = group.locator('.el-menu-item .menu-label__main');
    await expect(items).toHaveCount(labels.length);
    for (let i = 0; i < labels.length; i++) {
      await expect(items.nth(i)).toHaveText(new RegExp(`^${labels[i]}$`));
    }
  }

  test('should display menu groups and submenus matching the redefined spec', async ({ page }) => {
    await page.goto('/dashboard');

    // Sidebar should be visible
    await expect(page.locator('.app-sidebar')).toBeVisible();

    // 1. Verify top-level items exist
    await expect(mainLabel(page, '概览')).toBeVisible();
    await expect(mainLabel(page, '数据字典')).toBeVisible();

    // 1b. 顶栏右上角：工作空间（含徽标容器）与应用设置入口均可见
    const headerRight = page.locator('[data-testid="header-right"]');
    await expect(headerRight.locator('[data-testid="workspace-entry-btn"]')).toBeVisible();
    await expect(headerRight.locator('[data-testid="workspace-entry-badge"]')).toBeVisible();
    await expect(headerRight.locator('[data-testid="app-settings-entry-btn"]')).toBeVisible();
    // 侧边栏不含工作空间/系统菜单项
    await expect(mainLabel(page, '工作空间')).toHaveCount(0);
    await expect(mainLabel(page, '系统')).toHaveCount(0);

    // 1c. 底栏（我的下方）：管理员可见合并后的"系统"入口
    const footer = page.locator('[data-testid="sidebar-footer"]');
    await expect(footer.locator('[data-testid="nav-system"]')).toBeVisible();

    // 2. Verify sub-menu group titles（系统已移至"我的"之下的底栏，不在侧边栏菜单）
    for (const group of ['概念模块', '表示模块', '基础与核心模块', '标识与命名模块', '注册模块', '分类模块', '项目映射模块']) {
      await expect(mainLabel(page, group)).toBeVisible();
    }

    // 3. Verify items of each group in exact order
    await expectGroupItems(page, '概念模块', [
      '对象类',
      '特性',
      '数据元概念',
      '概念域',
      '值含义',
      '维度',
      '计量类',
      '计量单位',
    ]);

    await expectGroupItems(page, '表示模块', [
      '数据元',
      '值域',
      '数据类型方案',
      '数据类型',
    ]);

    await expectGroupItems(page, '基础与核心模块', [
      '个人',
      '组织',
      '角色',
      '联系人',
      '文档类型',
      '语言',
      '参考文档',
      '上下文',
    ]);

    await expectGroupItems(page, '标识与命名模块', [
      '命名空间',
      '命名规范',
    ]);

    await expectGroupItems(page, '注册模块', [
      '注册机构',
      '注册规范',
      '注册人员',
      '注册流程',
      '注册项',
    ]);

    await expectGroupItems(page, '分类模块', [
      '分类方案',
      '分类项',
    ]);

    await expectGroupItems(page, '项目映射模块', [
      '条目映射',
    ]);
  });

  test('should navigate correctly from newly wired menu entries', async ({ page }) => {
    await page.goto('/dashboard');
    await expect(page.locator('.app-sidebar')).toBeVisible();

    // 工作空间（顶栏右上角入口）
    await page.locator('[data-testid="workspace-entry-btn"]').click();
    await expect(page).toHaveURL(/\/workspace/);

    // 概念模块 → 值含义
    let group = await expandGroup(page, '概念模块');
    await group.locator('.el-menu-item').filter({ has: mainLabel(page, '值含义') }).click();
    await expect(page).toHaveURL(/\/value-meanings/);

    // 基础与核心模块 → 文档类型
    group = await expandGroup(page, '基础与核心模块');
    await group.locator('.el-menu-item').filter({ has: mainLabel(page, '文档类型') }).click();
    await expect(page).toHaveURL(/\/document-types/);

    // 注册模块 → 注册流程（注册流程展示页）
    group = await expandGroup(page, '注册模块');
    await group.locator('.el-menu-item').filter({ has: mainLabel(page, '注册流程') }).click();
    await expect(page).toHaveURL(/\/registration-process/);

    // 项目映射模块 → 条目映射
    group = await expandGroup(page, '项目映射模块');
    await group.locator('.el-menu-item').filter({ has: mainLabel(page, '条目映射') }).click();
    await expect(page).toHaveURL(/\/item-mappings/);
  });

  test('should navigate to placeholder pages for planned menu items', async ({ page }) => {
    await page.goto('/dashboard');
    await expect(page.locator('.app-sidebar')).toBeVisible();

    // 概念模块 → 维度（已实现为维度管理页，不再是占位页）
    let group = await expandGroup(page, '概念模块');
    await group.locator('.el-menu-item').filter({ has: mainLabel(page, '维度') }).click();
    await expect(page).toHaveURL(/\/dimensionality/);
    await expect(page.locator('.placeholder-title')).toHaveCount(0);
    await expect(page.locator('h2', { hasText: '维度' })).toBeVisible();

    // 基础与核心模块 → 角色（已实现为角色管理页，不再是占位页）
    group = await expandGroup(page, '基础与核心模块');
    await group.locator('.el-menu-item').filter({ has: mainLabel(page, '角色') }).click();
    await expect(page).toHaveURL(/\/roles/);
    await expect(page.locator('.placeholder-title')).toHaveCount(0);
    await expect(page.locator('h2', { hasText: '角色' })).toBeVisible();

    // 分类模块 → 分类项（规划中）
    group = await expandGroup(page, '分类模块');
    await group.locator('.el-menu-item').filter({ has: mainLabel(page, '分类项') }).click();
    await expect(page).toHaveURL(/\/classification-items/);
    await expect(page.locator('.placeholder-title')).toHaveText('分类项');
  });

  test('should no longer expose removed mapping menu entries', async ({ page }) => {
    await page.goto('/dashboard');
    await expect(page.locator('.app-sidebar')).toBeVisible();

    // 旧“分类与映射”组已拆分：映射网络 / 匹配结果管理不再出现在侧边栏
    await expect(mainLabel(page, '映射网络')).toHaveCount(0);
    await expect(mainLabel(page, '匹配结果管理')).toHaveCount(0);
  });

  test('should hide system menu for non-admin user', async ({ page }) => {
    // Override the mock to return user with REGISTRAR role (non-admin)
    await page.route('**/api/v1/auth/me', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          data: {
            id: 'user-002',
            username: 'registrar_user',
            sysRole: null,
            isRegistrar: true,
            enabled: true,
          },
        }),
      });
    });

    await page.goto('/dashboard');
    await expect(page.locator('.app-sidebar')).toBeVisible();

    // 非管理员：底栏无"系统"入口；应用设置入口仍可见；侧边栏无系统组
    const footer = page.locator('[data-testid="sidebar-footer"]');
    await expect(footer.locator('[data-testid="nav-system"]')).toHaveCount(0);
    await expect(
      page.locator('[data-testid="header-right"]').locator('[data-testid="app-settings-entry-btn"]')
    ).toBeVisible();
    await expect(
      page.locator('.el-sub-menu__title').filter({ has: mainLabel(page, '系统') })
    ).toHaveCount(0);
  });

  test('should collapse sidebar when collapse button is clicked', async ({ page }) => {
    await page.goto('/dashboard');
    await expect(page.locator('.app-sidebar')).toBeVisible();

    // Ensure it is not collapsed initially
    await expect(page.locator('.app-sidebar')).not.toHaveClass(/collapsed/);

    // Click collapse button
    await page.locator('.collapse-btn').click();

    // Verify sidebar has collapsed class
    await expect(page.locator('.app-sidebar')).toHaveClass(/collapsed/);
  });

  // ─── Guard: "我的" layout position ────────────────────────────────────────
  // This test exists solely to prevent regressions where AI edits accidentally
  // relocate the "我的" navigation link away from the sidebar bottom.
  test('should have "我的" pinned to sidebar footer, NOT in the header', async ({ page }) => {
    await page.goto('/dashboard');
    await expect(page.locator('.app-sidebar')).toBeVisible();

    // 1. The nav-my element must exist inside the sidebar footer
    const navMy = page.locator('[data-testid="nav-my"]');
    await expect(navMy).toBeVisible();

    // 2. It must be a descendant of .app-sidebar (sidebar), not the header
    const sidebarFooter = page.locator('[data-testid="sidebar-footer"]');
    await expect(sidebarFooter).toBeVisible();

    // Confirm sidebar-footer is inside .app-sidebar
    const sidebarContainsFooter = await page.locator('.app-sidebar [data-testid="sidebar-footer"]').count();
    expect(sidebarContainsFooter).toBe(1);

    // 3. The header-right must NOT contain any "我的" button
    const headerRight = page.locator('[data-testid="header-right"]');
    await expect(headerRight).toBeVisible();

    // header-right should contain ONLY the user-tag and logout button — no nav-my
    const navMyInHeader = headerRight.locator('[data-testid="nav-my"]');
    await expect(navMyInHeader).toHaveCount(0);

    // 4. Clicking nav-my should navigate to /my-profile
    await navMy.click();
    await expect(page).toHaveURL(/\/my-profile/);
  });

  test('should keep "我的" visible when sidebar is collapsed (icon-only)', async ({ page }) => {
    await page.goto('/dashboard');
    await expect(page.locator('.app-sidebar')).toBeVisible();

    // Collapse the sidebar
    await page.locator('.collapse-btn').click();
    await expect(page.locator('.app-sidebar')).toHaveClass(/collapsed/);

    // nav-my item itself must still be visible (icon shown even when collapsed)
    await expect(page.locator('[data-testid="nav-my"]')).toBeVisible();

    // The text label should be hidden when collapsed
    await expect(page.locator('[data-testid="nav-my"] .sidebar-footer-label')).toHaveCount(0);
  });
});
