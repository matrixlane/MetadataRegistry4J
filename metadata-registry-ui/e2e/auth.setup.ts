import { Page } from '@playwright/test';

export async function setupAuthenticatedPage(page: Page) {
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
          displayName: 'Test User',
          role: 'ADMIN',
          email: 'test@example.com',
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
            displayName: 'Test User',
            role: 'ADMIN',
            email: 'test@example.com',
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
