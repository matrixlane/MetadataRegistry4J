import { test, expect } from '@playwright/test';
import fs from 'fs';

test('debug real emergency login flow and navigate to profile', async ({ page }) => {
  page.on('console', msg => {
    console.log(`[BROWSER CONSOLE] [${msg.type()}] ${msg.text()}`);
  });
  page.on('pageerror', err => {
    console.log('BROWSER EXCEPTION:', err.stack || err.message);
  });

  const activeRequests = new Set<string>();
  page.on('request', request => {
    const url = request.url();
    if (url.includes('/api/')) {
      activeRequests.add(url);
      console.log(`>> REQUEST: ${request.method()} ${url}`);
    }
  });
  page.on('response', async response => {
    const url = response.url();
    if (url.includes('/api/')) {
      const status = response.status();
      console.log(`<< RESPONSE: ${status} ${url}`);
      if (status >= 400) {
        try {
          const text = await response.text();
          console.log(`<< ERROR RESPONSE BODY: ${text}`);
        } catch (e) {
          console.log('<< ERROR RESPONSE BODY: (failed to read)');
        }
      }
    }
  });

  // Navigate to login
  await page.goto('/login');
  
  // Get emergency code from file (so it works dynamically)
  const emergencyCode = fs.readFileSync('../emergency-code.txt', 'utf8').trim();
  console.log('Using emergency code from file:', emergencyCode);

  // Fill credentials and login using emergency code
  await page.fill('input[placeholder*="用户名"]', 'admin');
  await page.fill('input[placeholder*="密码"]', emergencyCode);
  await page.click('button:has-text("登录")');

  // Wait for dialog
  const dialog = page.locator('.el-dialog').filter({ hasText: '安全提示：需要修改密码' });
  await expect(dialog).toBeVisible();
  console.log('Dialog is visible on dashboard.');

  // Now, let's change forceChangePassword to false temporarily in client auth store memory 
  // OR simulate navigating to /my-profile by directly modifying URL or clicking
  // Wait, if we try to navigate to /my-profile directly:
  console.log('Navigating directly to /my-profile...');
  await page.goto('/my-profile');

  // Verify dialog is visible again (meaning the 498 response successfully triggered it)
  await expect(dialog).toBeVisible();
  
  // Verify that the crash screen "页面加载失败" is NOT visible
  const errorTitle = page.locator('.error-title');
  await expect(errorTitle).toHaveCount(0);
  console.log('Verified: No crash screen "页面加载失败" is visible.');

  // Fill new password only (old password field is hidden for emergency login)
  const newPassword = `AdminNew${Math.random().toString(36).substring(2, 7)}`;
  await dialog.locator('input[placeholder="请输入新密码"]').fill(newPassword);
  await dialog.locator('input[placeholder="请再次输入新密码"]').fill(newPassword);
  
  // Submit password change
  await dialog.locator('button:has-text("确认修改并登录")').click();

  // Verify dialog is closed
  await expect(dialog).not.toBeVisible();
  console.log('Password successfully changed, and dialog is closed.');

  // Verify we are on profile page and the profile page loads successfully without crash
  await expect(page).toHaveURL(/\/my-profile/);
  await expect(page.locator('text=编辑个人资料')).toBeVisible();
  console.log('Successfully loaded profile page after change password!');

  // Restore password back to Admin@123 for other tests
  await page.evaluate(async (oldPwd) => {
    const token = localStorage.getItem('token');
    const response = await fetch('/api/v1/auth/change-password', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify({
        oldPassword: oldPwd,
        newPassword: 'Admin@123'
      })
    });
    if (!response.ok) {
      throw new Error(`Failed to restore password: ${response.status} ${await response.text()}`);
    }
  }, newPassword);
  console.log('Restored admin password to Admin@123');
});
