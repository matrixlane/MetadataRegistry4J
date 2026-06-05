import { test, expect } from '@playwright/test';
import { setupAuthenticatedPage } from './auth.setup';

test.describe('Sidebar Menu Reorganization', () => {
  test.beforeEach(async ({ page }) => {
    await setupAuthenticatedPage(page);

    // Catch-all mock for other API endpoints to prevent 401 redirects when page initializes
    await page.route('**/api/v1/**', async (route) => {
      const url = route.request().url();
      
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

  test('should display menu groups and submenus matching the new spec', async ({ page }) => {
    await page.goto('/dashboard');

    // Sidebar should be visible
    await expect(page.locator('.app-sidebar')).toBeVisible();

    // 1. Verify primary elements and group titles exist
    await expect(page.locator('.el-menu-item').filter({ hasText: /^概览$/ })).toBeVisible();
    await expect(page.locator('.el-menu-item').filter({ hasText: /^数据字典$/ })).toBeVisible();

    // 2. Verify sub-menu title texts
    await expect(page.locator('.el-sub-menu__title').filter({ hasText: /^注册通用设施$/ })).toBeVisible();
    await expect(page.locator('.el-sub-menu__title').filter({ hasText: /^主体与文档$/ })).toBeVisible();
    await expect(page.locator('.el-sub-menu__title').filter({ hasText: /^概念区域$/ })).toBeVisible();
    await expect(page.locator('.el-sub-menu__title').filter({ hasText: /^表示区域$/ })).toBeVisible();
    await expect(page.locator('.el-sub-menu__title').filter({ hasText: /^分类与映射$/ })).toBeVisible();
    await expect(page.locator('.el-sub-menu__title').filter({ hasText: /^系统$/ })).toBeVisible();

    // Verify items under '注册通用设施' in exact order
    const regSubmenu = page.locator('.el-sub-menu').filter({
      has: page.locator('> .el-sub-menu__title').filter({ hasText: /^注册通用设施$/ })
    });
    await regSubmenu.locator('> .el-sub-menu__title').click();

    const regItems = regSubmenu.locator('.el-menu-item');
    await expect(regItems.nth(0)).toHaveText(/^注册机构$/);
    await expect(regItems.nth(1)).toHaveText(/^注册规范$/);
    await expect(regItems.nth(2)).toHaveText(/^注册人员$/);
    await expect(regItems.nth(3)).toHaveText(/^命名空间$/);
    await expect(regItems.nth(4)).toHaveText(/^语言$/);
    await expect(regItems.nth(5)).toHaveText(/^上下文$/);

    // 3. Expand Concept Region and verify items
    const conceptSubmenu = page.locator('.el-sub-menu').filter({
      has: page.locator('> .el-sub-menu__title').filter({ hasText: /^概念区域$/ })
    });
    await conceptSubmenu.locator('> .el-sub-menu__title').click();

    await expect(conceptSubmenu.locator('.el-menu-item').filter({ hasText: /^对象类$/ })).toBeVisible();
    await expect(conceptSubmenu.locator('.el-menu-item').filter({ hasText: /^特性$/ })).toBeVisible();
    await expect(conceptSubmenu.locator('.el-menu-item').filter({ hasText: /^数据元概念$/ })).toBeVisible();
    await expect(conceptSubmenu.locator('.el-menu-item').filter({ hasText: /^概念域$/ })).toBeVisible();
    await expect(conceptSubmenu.locator('.el-menu-item').filter({ hasText: /^计量单位$/ })).toBeVisible();
    await expect(conceptSubmenu.locator('.el-menu-item').filter({ hasText: /^计量类$/ })).toBeVisible();
    await expect(conceptSubmenu.locator('.el-menu-item').filter({ hasText: /^维度$/ })).toBeVisible();

    // 4. Expand Representation Region and verify items
    const representationSubmenu = page.locator('.el-sub-menu').filter({
      has: page.locator('> .el-sub-menu__title').filter({ hasText: /^表示区域$/ })
    });
    await representationSubmenu.locator('> .el-sub-menu__title').click();
    await expect(representationSubmenu.locator('.el-menu-item').filter({ hasText: /^数据元$/ })).toBeVisible();
    await expect(representationSubmenu.locator('.el-menu-item').filter({ hasText: /^值域$/ })).toBeVisible();
    await expect(representationSubmenu.locator('.el-menu-item').filter({ hasText: /^数据类型$/ })).toBeVisible();
    await expect(representationSubmenu.locator('.el-menu-item').filter({ hasText: /^数据类型方案$/ })).toBeVisible();
  });

  test('should navigate correctly and display placeholder page when clicking 维度', async ({ page }) => {
    await page.goto('/dashboard');
    await expect(page.locator('.app-sidebar')).toBeVisible();

    // Expand Concept Region
    const conceptSubmenu = page.locator('.el-sub-menu').filter({
      has: page.locator('> .el-sub-menu__title').filter({ hasText: /^概念区域$/ })
    });
    await conceptSubmenu.locator('> .el-sub-menu__title').click();

    // Click on 维度
    await conceptSubmenu.locator('.el-menu-item').filter({ hasText: /^维度$/ }).click();

    // Verify redirect and placeholder content
    await expect(page).toHaveURL(/\/dimensionality/);
    await expect(page.locator('.placeholder-title')).toHaveText('维度');
    await expect(page.locator('.placeholder-desc')).toHaveText('功能开发中，敬请期待');
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

    // Verify system submenu is NOT visible
    await expect(page.locator('.el-sub-menu__title').filter({ hasText: /^系统$/ })).toHaveCount(0);
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
