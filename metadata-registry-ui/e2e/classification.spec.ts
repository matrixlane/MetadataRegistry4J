import { test, expect } from '@playwright/test';
import { setupAuthenticatedPage } from './auth.setup';

test.describe('Classification Scheme & Node Management E2E', () => {
  test.beforeEach(async ({ page }) => {
    await setupAuthenticatedPage(page);

    page.on('console', msg => {
      console.log(`BROWSER CONSOLE [${msg.type()}]: ${msg.text()}`);
    });

    // Classification schemes list/create (matches classification-schemes with any query parameters)
    await page.route('**/api/v1/classification-schemes**', async (route) => {
      const method = route.request().method();
      const url = route.request().url();
      
      // Fallback for more specific endpoints
      if (url.includes('/export') || url.includes('/nodes') || url.includes('/import')) {
        await route.fallback();
        return;
      }

      if (method === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 0,
            data: {
              content: [
                {
                  id: 'scheme-001',
                  schemeName: 'Test Scheme 1',
                  description: 'First test scheme',
                  version: 'v1.0',
                  status: 'DRAFT',
                },
              ],
              totalElements: 1,
              number: 0,
              size: 100,
            },
          }),
        });
      } else if (method === 'POST') {
        const body = route.request().postDataJSON();
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 0,
            data: {
              id: 'scheme-new',
              schemeName: body.schemeName,
              description: body.description,
              version: body.version,
              status: body.status,
            },
          }),
        });
      }
    });

    // Specific scheme detail/update/delete
    await page.route('**/api/v1/classification-schemes/scheme-001**', async (route) => {
      const method = route.request().method();
      const url = route.request().url();
      
      if (url.includes('/export') || url.includes('/nodes')) {
        await route.fallback();
        return;
      }

      if (method === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 0,
            data: {
              id: 'scheme-001',
              schemeName: 'Test Scheme 1',
              description: 'First test scheme',
              version: 'v1.0',
              status: 'DRAFT',
            },
          }),
        });
      } else if (method === 'PUT') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 0,
            data: {
              id: 'scheme-001',
              schemeName: 'Updated Scheme 1',
              description: 'Updated description',
              version: 'v1.1',
              status: 'PUBLISHED',
            },
          }),
        });
      } else if (method === 'DELETE') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 0,
            message: 'Success',
          }),
        });
      }
    });

    // Mock tree children
    await page.route('**/api/v1/classification-schemes/*/nodes/children**', async (route) => {
      const url = new URL(route.request().url());
      const parentId = url.searchParams.get('parentId');

      if (!parentId) {
        // Root Level
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 0,
            data: [
              {
                id: 'node-001',
                label: 'Root Node 1',
                nodeCode: 'R1',
                sortOrder: 10,
                hasChildren: true,
              },
            ],
          }),
        });
      } else if (parentId === 'node-001') {
        // Child Level
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 0,
            data: [
              {
                id: 'node-002',
                label: 'Child Node 1.1',
                nodeCode: 'R1.1',
                sortOrder: 5,
                hasChildren: false,
              },
            ],
          }),
        });
      }
    });

    // Mock individual node retrieval
    await page.route('**/api/v1/classification-schemes/*/nodes/node-001**', async (route) => {
      const method = route.request().method();
      if (method === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 0,
            data: {
              id: 'node-001',
              nodeCode: 'R1',
              nodeName: 'Root Node 1',
              description: 'Description for Root Node 1',
              definition: 'Definition for Root Node 1',
              sortOrder: 10,
              parentId: null,
              schemeId: 'scheme-001',
              schemeName: 'Test Scheme 1',
              hasChildren: true,
              createdAt: '2026-05-23T00:00:00Z',
              updatedAt: '2026-05-23T00:00:00Z',
            },
          }),
        });
      } else if (method === 'PUT') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 0,
            data: {
              id: 'node-001',
              nodeCode: 'R1',
              nodeName: 'Root Node 1 (Edited)',
              description: 'Updated Description',
              definition: 'Updated Definition',
              sortOrder: 20,
            },
          }),
        });
      } else if (method === 'DELETE') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 0,
            message: 'Success',
          }),
        });
      }
    });

    // Mock flat node list and node creation
    await page.route('**/api/v1/classification-schemes/*/nodes', async (route) => {
      const method = route.request().method();
      const url = route.request().url();
      if (url.includes('/move') || url.includes('/cascade')) {
        await route.fallback();
        return;
      }
      if (method === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 0,
            data: [
              { id: 'node-001', nodeCode: 'R1', nodeName: 'Root Node 1' },
              { id: 'node-002', nodeCode: 'R1.1', nodeName: 'Child Node 1.1' },
            ],
          }),
        });
      } else if (method === 'POST') {
        const body = route.request().postDataJSON();
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 0,
            data: {
              id: 'node-new',
              nodeCode: body.nodeCode || 'R2',
              nodeName: body.nodeName || 'Root Node 2',
              sortOrder: body.sortOrder || 10,
            },
          }),
        });
      } else {
        await route.fallback();
      }
    });

    // Mock move node endpoint
    await page.route('**/api/v1/classification-schemes/*/nodes/*/move**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          data: {
            id: 'node-001',
            nodeCode: 'R1',
            nodeName: 'Root Node 1',
            parentId: 'node-002',
          },
        }),
      });
    });

    // Mock cascade delete
    await page.route('**/api/v1/classification-schemes/*/nodes/*/cascade**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          message: 'Success',
        }),
      });
    });

    // Catch-all mock for other API endpoints to prevent 401 redirects when page initializes
    await page.route('**/api/v1/**', async (route) => {
      const url = route.request().url();

      if (url.includes('/classification-schemes')) {
        await route.fallback();
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

  test('should load page, select scheme, explore lazy tree and view node detail', async ({ page }) => {
    await page.goto('/classification');
    await expect(page.locator('.app-sidebar')).toBeVisible();

    // Breadcrumb and panel should load
    await expect(page.locator('.classification-tree-browser')).toBeVisible();
    
    // Select scheme
    await page.locator('.panel-header .el-select').click();
    await page.locator('.el-select-dropdown__item:has-text("Test Scheme 1")').click();

    // Verify scheme details loaded and tree rendered
    await expect(page.locator('.el-tree')).toBeVisible();
    await expect(page.locator('.tree-node-label:has-text("Root Node 1")')).toBeVisible();

    // Click tree node to load details in the right panel
    await page.locator('.tree-node-label:has-text("Root Node 1")').click();
    await expect(page.locator('.detail-panel .el-descriptions')).toBeVisible();
    await expect(page.locator('.detail-panel').locator('text=Root Node 1').first()).toBeVisible();

    // Test lazy load child node
    const treeNode = page.locator('.el-tree-node').filter({ hasText: 'Root Node 1' }).first();
    await treeNode.locator('.el-tree-node__expand-icon').click();
    
    // Verify child node fetched and displayed
    await expect(page.locator('.tree-node-label:has-text("Child Node 1.1")')).toBeVisible();
  });

  test.skip('should create, edit and delete classification scheme', async ({ page }) => {
    await page.goto('/classification');
    await expect(page.locator('.app-sidebar')).toBeVisible();

    // 1. Create scheme
    await page.locator('button:has-text("新建方案")').click();
    await expect(page.locator('.el-dialog').filter({ hasText: '新建分类方案' })).toBeVisible();

    // Fill scheme fields
    await page.locator('input[placeholder="例如：国际疾病分类ICD-10"]').fill('New ICD Scheme');
    await page.locator('input[placeholder="例如：2023 Edition"]').fill('v1.0');
    await page.locator('button:has-text("确定")').click();

    // Check successful creation notification
    await expect(page.locator('.el-dialog').filter({ hasText: '新建分类方案' })).not.toBeVisible();

    // 2. Edit current scheme
    await page.locator('.panel-header .el-select').click();
    await page.locator('.el-select-dropdown__item:has-text("Test Scheme 1")').click();
    await page.locator('button:has-text("修改")').click();
    await expect(page.locator('.el-dialog').filter({ hasText: '修改分类方案' })).toBeVisible();
    
    await page.locator('input[placeholder="例如：国际疾病分类ICD-10"]').fill('Updated ICD Scheme');
    await page.locator('.el-dialog >> button:has-text("确定")').click();
    await expect(page.locator('.el-dialog').filter({ hasText: '修改分类方案' })).not.toBeVisible();

    // 3. Delete scheme
    await page.locator('button:has-text("删除")').click();
    await page.locator('.el-popover:visible >> button:has-text("确定")').click();
  });

  test.skip('should support creating, updating, moving and deleting classification nodes', async ({ page }) => {
    await page.goto('/classification');
    await expect(page.locator('.app-sidebar')).toBeVisible();
    
    // Select scheme
    await page.locator('.panel-header .el-select').click();
    await page.locator('.el-select-dropdown__item:has-text("Test Scheme 1")').click();

    // 1. Create a root node
    await page.locator('button:has-text("新增根节点")').click();
    await expect(page.locator('.el-dialog').filter({ hasText: '新增根分类节点' })).toBeVisible();
    
    await page.locator('input[placeholder="建议使用点分/阶梯编码，例如: A00.0"]').fill('A01');
    await page.locator('input[placeholder="中文或英文分类名称"]').fill('Infectious Diseases');
    await page.locator('.el-dialog >> button:has-text("确定")').click();
    await expect(page.locator('.el-dialog').filter({ hasText: '新增根分类节点' })).not.toBeVisible();

    // Click node to open operations
    await page.locator('.tree-node-label:has-text("Root Node 1")').click();
    
    // 2. Edit node
    await page.locator('button:has-text("编辑节点")').click();
    await expect(page.locator('.el-dialog').filter({ hasText: '编辑分类节点' })).toBeVisible();
    await page.locator('input[placeholder="中文或英文分类名称"]').fill('Root Node 1 (Edited)');
    await page.locator('.el-dialog >> button:has-text("确定")').click();
    await expect(page.locator('.el-dialog').filter({ hasText: '编辑分类节点' })).not.toBeVisible();

    // 3. Move node
    await page.locator('button:has-text("移动节点")').click();
    await expect(page.locator('.el-dialog').filter({ hasText: '移动分类节点' })).toBeVisible();
    
    // Select new parent from dropdown
    await page.locator('.move-dialog-content .el-select').click();
    await page.locator('.el-select-dropdown__item:has-text("Child Node 1.1")').click();
    await page.locator('button:has-text("确定移动")').click();
    await expect(page.locator('.el-dialog').filter({ hasText: '移动分类节点' })).not.toBeVisible();

    // 4. Cascade delete node
    await page.locator('button:has-text("级联删除")').click();
    await page.locator('.el-popover:visible >> button:has-text("确定级联删除")').click();
  });
});
