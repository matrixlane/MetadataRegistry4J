import { Page } from '@playwright/test';

export async function setupAuthenticatedPage(page: Page) {
  // Mock init-wizard status — system is initialized.
  // This is required because the router guard now calls checkInitialized()
  // on every protected route navigation.
  await page.route('**/api/v1/init-wizard/status', async (route) => {
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
  });

  // Intercept auth endpoints to mock login state
  await page.route('**/api/v1/auth/me', async (route) => {
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
  });

  // Intercept login endpoint
  await page.route('**/api/v1/auth/login', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 0,
        data: {
          token: 'mock-jwt-token-for-e2e-tests',
          user: {
            id: 'user-001',
            username: 'testuser',
            sysRole: 'SUPER_ADMIN',
            isRegistrar: true,
            enabled: true,
          },
        },
      }),
    });
  });

  // Inject token directly via localStorage
  await page.addInitScript(() => {
    localStorage.setItem('token', 'mock-jwt-token-for-e2e-tests');
  });
}
