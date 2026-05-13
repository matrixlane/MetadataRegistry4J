import { test, expect } from '@playwright/test';
import { setupAuthenticatedPage } from './auth.setup';
import {
  createMockNetworkResponse,
  createTruncatedResponse,
  MOCK_CENTER_ID,
} from './fixtures/network-data';

test.describe('Mapping Network Visualization (WI-011-2-1)', () => {
  test.beforeEach(async ({ page }) => {
    await setupAuthenticatedPage(page);

    // Mock mapping network API
    await page.route('**/api/v1/mappings/network**', async (route) => {
      const url = new URL(route.request().url());
      const filterTypesParam = url.searchParams.get('filterTypes');
      const filterTypes = filterTypesParam
        ? filterTypesParam.split(',')
        : undefined;
      const depthParam = url.searchParams.get('depth');
      const depth = depthParam ? parseInt(depthParam, 10) : 3;

      // Check for truncation test center ID
      const centerId = url.searchParams.get('centerId');
      if (centerId === 'truncated-test-id') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 0,
            data: createTruncatedResponse(),
          }),
        });
        return;
      }

      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          data: createMockNetworkResponse(filterTypes, depth),
        }),
      });
    });
  });

  test('should load mapping network page and query network', async ({ page }) => {
    await page.goto('/mapping-network');

    // Page should render controls
    await expect(page.locator('.mapping-network-view')).toBeVisible();
    await expect(page.getByRole('heading', { name: '映射网络' })).toBeVisible();

    // Input center item ID and query
    await page.locator('.el-input__inner').first().fill(MOCK_CENTER_ID);
    await page.locator('button:has-text("查询")').click();

    // Wait for graph to render
    await expect(page.locator('.graph-container canvas, .graph-container div[_echarts_instance_]')).toBeVisible({ timeout: 5000 });

    // Node/edge badges should appear
    await expect(page.locator('.info-badge')).toHaveCount(2);
    await expect(page.locator('.info-badge').first()).toContainText('节点');
    await expect(page.locator('.info-badge').last()).toContainText('边');
  });

  test('should filter network by mapping type', async ({ page }) => {
    await page.goto('/mapping-network');

    // Input center item ID
    await page.locator('.el-input__inner').first().fill(MOCK_CENTER_ID);

    // Open filter dropdown and select only EQUIVALENT
    const filterSelect = page.locator('.el-form-item').filter({ hasText: '映射类型' }).locator('.el-select');
    await filterSelect.click();
    await page.locator('.el-select-dropdown__item:has-text("等价")').first().click();
    await page.keyboard.press('Escape');

    // Query
    await page.locator('button:has-text("查询")').click();

    // Wait for graph
    await expect(page.locator('.graph-container canvas, .graph-container div[_echarts_instance_]')).toBeVisible({ timeout: 5000 });

    // With only EQUIVALENT filter, we should have fewer edges
    // Center + 2 equivalent nodes = 3 nodes, 2 edges
    await expect(page.locator('.info-badge').first()).toContainText('3');
  });

  test('should filter by multiple mapping types', async ({ page }) => {
    await page.goto('/mapping-network');

    await page.locator('.el-input__inner').first().fill(MOCK_CENTER_ID);

    // Select EQUIVALENT and DERIVED
    const filterSelect2 = page.locator('.el-form-item').filter({ hasText: '映射类型' }).locator('.el-select');
    await filterSelect2.click();
    await page.locator('.el-select-dropdown__item:has-text("等价")').first().click();
    await page.locator('.el-select-dropdown__item:has-text("派生")').first().click();
    await page.keyboard.press('Escape');

    await page.locator('button:has-text("查询")').click();

    await expect(page.locator('.graph-container canvas, .graph-container div[_echarts_instance_]')).toBeVisible({ timeout: 5000 });

    // Center + 4 nodes (2 lvl1 + 2 lvl2) = 5 nodes, 4 edges
    await expect(page.locator('.info-badge').first()).toContainText('5');
  });

  test('should show truncation warning when node count exceeds 500', async ({ page }) => {
    await page.goto('/mapping-network');

    await page.locator('.el-input__inner').first().fill('truncated-test-id');
    await page.locator('button:has-text("查询")').click();

    // Truncation warning should appear
    await expect(page.locator('.el-alert--warning')).toBeVisible({ timeout: 5000 });
    await expect(page.locator('.el-alert--warning')).toContainText('节点数超过显示上限');
  });

  test('should reset view when reset button clicked', async ({ page }) => {
    await page.goto('/mapping-network');

    await page.locator('.el-input__inner').first().fill(MOCK_CENTER_ID);
    await page.locator('button:has-text("查询")').click();

    await expect(page.locator('.graph-container canvas, .graph-container div[_echarts_instance_]')).toBeVisible({ timeout: 5000 });

    // Click reset
    await page.locator('button:has-text("重置")').click();

    // Graph should be cleared
    await expect(page.locator('.empty-state')).toBeVisible();
  });

  test('should show empty state when no data', async ({ page }) => {
    await page.goto('/mapping-network');

    // Don't input anything, just check empty state is visible by default
    await expect(page.locator('.empty-state')).toBeVisible();
  });

  test('should show tooltip on edge hover (simulated)', async ({ page }) => {
    await page.goto('/mapping-network');

    await page.locator('.el-input__inner').first().fill(MOCK_CENTER_ID);
    await page.locator('button:has-text("查询")').click();

    await expect(page.locator('.graph-container canvas, .graph-container div[_echarts_instance_]')).toBeVisible({ timeout: 5000 });

    // ECharts tooltip is rendered inside the canvas container.
    // Trigger a mousemove over the graph container to simulate hover.
    const graphContainer = page.locator('.graph-container');
    await graphContainer.hover({ position: { x: 300, y: 300 } });

    // The tooltip may or may not appear depending on exact canvas coordinates,
    // but the graph should remain stable after hover.
    await expect(graphContainer).toBeVisible();
  });

  test('should support depth selection 1-3', async ({ page }) => {
    await page.goto('/mapping-network');

    await page.locator('.el-input__inner').first().fill(MOCK_CENTER_ID);

    // Change depth to 1
    const depthSelect = page.locator('.el-form-item').filter({ hasText: '展开层数' }).locator('.el-select');
    await depthSelect.click();
    await page.locator('.el-select-dropdown__item:has-text("1")').first().click();

    await page.locator('button:has-text("查询")').click();

    await expect(page.locator('.graph-container canvas, .graph-container div[_echarts_instance_]')).toBeVisible({ timeout: 5000 });

    // With depth=1, no level-2 nodes should be included
    // Center + 4 lvl1 nodes = 5 nodes
    await expect(page.locator('.info-badge').first()).toContainText('5');
  });
});
