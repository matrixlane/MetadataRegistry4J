import { test, expect } from '@playwright/test';

test.describe('Login Flow E2E Tests', () => {
  test.beforeEach(async ({ page }) => {
    // Catch-all mock for other API endpoints
    await page.route('**/api/v1/**', async (route) => {
      const url = route.request().url();
      
      // Default init-wizard status check to true
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

      // Default empty list response for other queries
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

  test('should successfully log in as a normal user', async ({ page }) => {
    // Intercept login endpoint
    await page.route('**/api/v1/auth/login', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          data: {
            token: 'normal-jwt-token',
            user: {
              id: 'user-normal',
              username: 'normaluser',
              sysRole: null,
              isRegistrar: false,
              enabled: true,
            },
          },
        }),
      });
    });

    // Intercept me endpoint
    await page.route('**/api/v1/auth/me', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          data: {
            id: 'user-normal',
            username: 'normaluser',
            sysRole: null,
            isRegistrar: false,
            enabled: true,
          },
        }),
      });
    });

    // Go to login page
    await page.goto('/login');
    await page.locator('input[placeholder="请输入用户名"]').fill('normaluser');
    await page.locator('input[placeholder="请输入密码"]').fill('Password123');
    await page.locator('button:has-text("登录")').click();

    // Verify redirect to dashboard
    await expect(page).toHaveURL(/\/dashboard/);
  });

  test('should force password change on Super Admin first login', async ({ page }) => {
    let passwordChanged = false;
    // Mock login returning forceChangePassword = true, lastLoginAt = null, emergencyLogin = false
    await page.route('**/api/v1/auth/login', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          data: {
            token: 'admin-first-jwt-token',
            user: {
              id: 'user-admin',
              username: 'admin',
              sysRole: 'SUPER_ADMIN',
              isRegistrar: true,
              enabled: true,
              forceChangePassword: !passwordChanged,
              lastLoginAt: null,
              emergencyLogin: false,
            },
          },
        }),
      });
    });

    // Mock me returning same user
    await page.route('**/api/v1/auth/me', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          data: {
            id: 'user-admin',
            username: 'admin',
            sysRole: 'SUPER_ADMIN',
            isRegistrar: true,
            enabled: true,
            forceChangePassword: !passwordChanged,
            lastLoginAt: null,
            emergencyLogin: false,
          },
        }),
      });
    });

    // Mock change-password endpoint
    await page.route('**/api/v1/auth/change-password', async (route) => {
      const data = route.request().postDataJSON();
      expect(data.newPassword).toBe('AdminNew123');
      // For first login, oldPassword is not sent or is empty
      passwordChanged = true;
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          message: 'Success',
        }),
      });
    });

    // Go to login page
    await page.goto('/login');
    await page.locator('input[placeholder="请输入用户名"]').fill('admin');
    await page.locator('input[placeholder="请输入密码"]').fill('any_pwd'); // satisfy client validation
    await page.locator('button:has-text("登录")').click();

    // Verify redirect to app layout, which triggers the dialog
    await expect(page).toHaveURL(/\/dashboard/);

    const dialog = page.locator('.el-dialog').filter({ hasText: '安全提示：需要修改密码' });
    await expect(dialog).toBeVisible();

    // Verify oldPassword field is hidden for first login admin
    await expect(dialog.locator('input[placeholder="请输入原密码或应急码"]')).toHaveCount(0);

    // Fill new password
    await dialog.locator('input[placeholder="请输入新密码"]').fill('AdminNew123');
    await dialog.locator('input[placeholder="请再次输入新密码"]').fill('AdminNew123');

    // Click submit in dialog
    await dialog.locator('button:has-text("确认修改并登录")').click();

    // Verify dialog closed and change password API was called
    await expect(dialog).not.toBeVisible();
    expect(passwordChanged).toBe(true);
  });

  test('should force password change and NOT require old password on emergency login', async ({ page }) => {
    let passwordChanged = false;
    // Mock login returning forceChangePassword = true, lastLoginAt = Date, emergencyLogin = true
    await page.route('**/api/v1/auth/login', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          data: {
            token: 'admin-emergency-jwt-token',
            user: {
              id: 'user-admin',
              username: 'admin',
              sysRole: 'SUPER_ADMIN',
              isRegistrar: true,
              enabled: true,
              forceChangePassword: !passwordChanged,
              lastLoginAt: '2026-06-05T09:00:00',
              emergencyLogin: true,
            },
          },
        }),
      });
    });

    // Mock me returning same user
    await page.route('**/api/v1/auth/me', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          data: {
            id: 'user-admin',
            username: 'admin',
            sysRole: 'SUPER_ADMIN',
            isRegistrar: true,
            enabled: true,
            forceChangePassword: !passwordChanged,
            lastLoginAt: '2026-06-05T09:00:00',
            emergencyLogin: true,
          },
        }),
      });
    });

    // Mock change-password endpoint — emergency login: oldPassword should be empty string
    await page.route('**/api/v1/auth/change-password', async (route) => {
      const data = route.request().postDataJSON();
      // Emergency login: old password field is hidden and sent as empty string
      expect(data.oldPassword).toBe('');
      expect(data.newPassword).toBe('AdminNew123');
      passwordChanged = true;
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          message: 'Success',
        }),
      });
    });

    // Go to login page
    await page.goto('/login');
    await page.locator('input[placeholder="请输入用户名"]').fill('admin');
    await page.locator('input[placeholder="请输入密码"]').fill('emergency_code');
    await page.locator('button:has-text("登录")').click();

    // Verify redirect to app layout
    await expect(page).toHaveURL(/\/dashboard/);

    const dialog = page.locator('.el-dialog').filter({ hasText: '安全提示：需要修改密码' });
    await expect(dialog).toBeVisible();

    // Verify oldPassword field is NOT visible for emergency login (new behavior)
    await expect(dialog.locator('input[placeholder="请输入原密码或应急码"]')).toHaveCount(0);

    // Fill only new password (no old password required)
    await dialog.locator('input[placeholder="请输入新密码"]').fill('AdminNew123');
    await dialog.locator('input[placeholder="请再次输入新密码"]').fill('AdminNew123');

    // Click submit in dialog
    await dialog.locator('button:has-text("确认修改并登录")').click();

    // Verify dialog closed and change password API was called
    await expect(dialog).not.toBeVisible();
    expect(passwordChanged).toBe(true);
  });


  test('should show friendly error message on wrong password', async ({ page }) => {
    await page.route('**/api/v1/auth/login', async (route) => {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 401,
          message: '用户名或密码错误',
        }),
      });
    });

    await page.goto('/login');
    await page.locator('input[placeholder="请输入用户名"]').fill('admin');
    await page.locator('input[placeholder="请输入密码"]').fill('wrong_password');
    await page.locator('button:has-text("登录")').click();

    // ElMessage error should be visible
    await expect(page.locator('.el-message.el-message--error')).toBeVisible();
    await expect(page.locator('.el-message.el-message--error')).toContainText('用户名或密码错误');

    // The ErrorBoundary full-page error should NOT appear
    await expect(page.locator('.error-boundary')).toHaveCount(0);
  });
});
