import { test, expect } from '@playwright/test';

test('debug real namespace page load', async ({ page }) => {
  page.on('console', msg => {
    console.log(`[BROWSER CONSOLE] [${msg.type()}] ${msg.text()}`);
  });
  page.on('pageerror', err => {
    console.log('BROWSER EXCEPTION:', err.stack || err.message);
  });

  // Track all requests and responses
  const activeRequests = new Set<string>();
  page.on('request', request => {
    const url = request.url();
    if (url.includes('/api/')) {
      activeRequests.add(url);
      console.log(`>> REQUEST: ${request.method()} ${url}`);
    }
  });
  page.on('requestfinished', request => {
    const url = request.url();
    if (url.includes('/api/')) {
      activeRequests.delete(url);
      console.log(`<< FINISHED: ${request.method()} ${url}`);
    }
  });
  page.on('requestfailed', request => {
    const url = request.url();
    if (url.includes('/api/')) {
      activeRequests.delete(url);
      console.error(`!! FAILED: ${request.method()} ${url} - ${request.failure()?.errorText}`);
    }
  });
  page.on('response', response => {
    const url = response.url();
    if (url.includes('/api/')) {
      console.log(`<< RESPONSE: ${response.status()} ${url}`);
    }
  });

  // Navigate to login
  await page.goto('/login');
  
  // Fill credentials and login
  await page.fill('input[placeholder*="用户名"]', 'admin');
  await page.fill('input[placeholder*="密码"]', 'Admin@123');
  await page.click('button:has-text("登录")');

  // Wait for navigation/dashboard
  await page.waitForURL('**/dashboard');
  console.log('Logged in successfully!');

  // Navigate to /namespaces
  await page.goto('/namespaces');
  console.log('Navigated to /namespaces, waiting for load...');

  // Wait for 8 seconds to capture all requests/rendering
  await page.waitForTimeout(8000);

  console.log('Active requests at the end:', Array.from(activeRequests));

  // Take screenshot
  await page.screenshot({ path: 'e2e/namespace_screenshot.png' });
  console.log('Screenshot saved to e2e/namespace_screenshot.png');
});
