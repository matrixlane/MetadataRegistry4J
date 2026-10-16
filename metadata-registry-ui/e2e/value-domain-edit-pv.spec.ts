import { test, expect } from '@playwright/test';

test('值域编辑弹窗显示许可值', async ({ page }) => {
  await page.goto('http://localhost:5173/value-domains');
  await page.waitForLoadState('networkidle');
  await page.locator('input').first().fill('结核患者发现方式');
  await page.getByRole('button', { name: '搜索' }).click();
  const tr = page.locator('tr', { hasText: '结核患者发现方式代码' }).first();
  await expect(tr).toBeVisible();
  await tr.getByRole('button', { name: '编辑' }).click();
  await page.getByRole('tab', { name: '属性' }).click();
  await page.waitForTimeout(500);
  // 7 个许可值全部渲染
  await expect(page.getByText('许可值 #7')).toBeVisible();
  // 首行取值 = 1
  await expect(page.locator('input').filter({ hasValue: '1' }).first()).toBeVisible();
  console.log('许可值行数:', await page.locator('text=许可值 #').count());
});
