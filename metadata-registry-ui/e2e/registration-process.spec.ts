import { test, expect, type Page, type Route } from '@playwright/test';
import { setupAuthenticatedPage } from './auth.setup';

/**
 * 注册流程（registration-process-designer）E2E。
 * mock 模式沿用 menu.spec.ts：setupAuthenticatedPage + catch-all api/v1 路由
 * （含 /startup/status 启动就绪门控）。
 */

// ── Fixtures ─────────────────────────────────────────────────────────────

/** ISO/IEC 11179-3 Table 30 十个注册状态 */
const ALL_STATUSES = [
  'INCOMPLETE', 'CANDIDATE', 'RECORDED', 'QUALIFIED', 'STANDARD',
  'PREFERRED_STANDARD', 'SUPERSEDED', 'RETIRED', 'HISTORICAL', 'APPLICATION',
];

/** §2.5/Annex D.2 迁移白名单（20 条，后端已按 ISO/IEC 11179-6:2023 Annex D.2 纠正）；
 * 10 条状态推进需审核（4.4 d) 向上 3 条 + D.4.2/D.4.3/D.2.9 Qualified+ 降级 7 条）；
 * RETIRED 与 HISTORICAL 无任何出边 */
const APPROVAL_TRANSITIONS = [
  'RECORDED->QUALIFIED', 'QUALIFIED->STANDARD', 'STANDARD->PREFERRED_STANDARD',
  'QUALIFIED->RETIRED', 'STANDARD->RETIRED', 'PREFERRED_STANDARD->RETIRED',
  'QUALIFIED->SUPERSEDED', 'STANDARD->SUPERSEDED', 'PREFERRED_STANDARD->SUPERSEDED',
  'SUPERSEDED->RETIRED',
];
const TRANSITION_WHITELIST: Array<[string, string]> = [
  ['INCOMPLETE', 'CANDIDATE'], ['INCOMPLETE', 'APPLICATION'], ['INCOMPLETE', 'HISTORICAL'],
  ['CANDIDATE', 'RECORDED'], ['CANDIDATE', 'APPLICATION'], ['CANDIDATE', 'HISTORICAL'],
  ['APPLICATION', 'CANDIDATE'],
  ['RECORDED', 'QUALIFIED'], ['RECORDED', 'RETIRED'], ['RECORDED', 'SUPERSEDED'],
  ['QUALIFIED', 'STANDARD'], ['QUALIFIED', 'RETIRED'], ['QUALIFIED', 'SUPERSEDED'],
  ['STANDARD', 'PREFERRED_STANDARD'], ['STANDARD', 'SUPERSEDED'], ['STANDARD', 'RETIRED'],
  ['PREFERRED_STANDARD', 'STANDARD'], ['PREFERRED_STANDARD', 'SUPERSEDED'], ['PREFERRED_STANDARD', 'RETIRED'],
  ['SUPERSEDED', 'RETIRED'],
];

const processDefinition = {
  authorityId: 'ra-001',
  transitions: TRANSITION_WHITELIST.map(([from, to]) => ({
    from,
    to,
    displayName: null,
    requiresApproval: APPROVAL_TRANSITIONS.includes(`${from}->${to}`),
    approverMode: 'ALL_REGISTRARS',
    quorumType: 'ANY_ONE',
    quorumCount: null,
    approverRegistrarIds: [],
    enabled: true,
  })),
};

const itemRow = {
  id: 'item-001',
  identifier: 'DE.00001',
  registrationStatus: 'RECORDED',
  effectiveDate: '2026-09-18T00:00:00',
  designations: [{ languageCode: 'zh-CN', value: '测试数据元' }],
};

// ── Mock helpers ─────────────────────────────────────────────────────────

async function fulfillJson(route: Route, data: unknown) {
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ code: 0, data }),
  });
}

/**
 * 注册本套件 API mock，返回请求记录数组（用于断言 status 参数与 approve 调用）。
 * approve 成功后，{id}/transitions 的第二次返回体现进度更新（1/1、已批准）。
 */
async function mockApis(page: Page) {
  const requests: string[] = [];
  let approveCalled = false;

  await page.route('**/api/v1/**', async (route) => {
    const url = route.request().url();
    const method = route.request().method();
    requests.push(`${method} ${url}`);

    if (url.includes('/api/v1/startup/status')) {
      return fulfillJson(route, { ready: true });
    }
    // 以下两个端点 setupAuthenticatedPage 已注册 mock，但本 catch-all 后注册优先匹配，
    // 必须显式放行，否则会被兜底空响应覆盖（initialized 缺失会跳回初始化向导）
    if (url.includes('/api/v1/init-wizard/status')) {
      return fulfillJson(route, { initialized: true, runningMode: 'SINGLE' });
    }
    if (url.includes('/api/v1/auth/me')) {
      return fulfillJson(route, {
        id: 'user-001',
        username: 'testuser',
        sysRole: 'SUPER_ADMIN',
        isRegistrar: true,
        enabled: true,
      });
    }
    if (url.includes('/api/v1/registration-process')) {
      return fulfillJson(route, processDefinition);
    }
    if (url.includes('/api/v1/administered-items/item-001/transitions') && method === 'GET') {
      return fulfillJson(route, [
        {
          to: 'QUALIFIED',
          displayName: '提交合格审定',
          requiresApproval: true,
          // 批准前 0/1；批准请求到达后 1/1 且置灰"已批准"
          approvedCount: approveCalled ? 1 : 0,
          quorum: 1,
          canExecute: false,
          canApprove: !approveCalled,
          approvedByMe: approveCalled,
        },
      ]);
    }
    if (url.includes('/api/v1/administered-items/item-001/transitions/approve') && method === 'POST') {
      approveCalled = true;
      return fulfillJson(route, {
        executed: true,
        transition: { from: 'RECORDED', to: 'QUALIFIED' },
      });
    }
    if (url.includes('/api/v1/administered-items') && method === 'GET') {
      return fulfillJson(route, {
        content: [itemRow],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      });
    }
    return fulfillJson(route, { content: [], totalElements: 0, number: 0, size: 20 });
  });

  return requests;
}

// ── Tests ────────────────────────────────────────────────────────────────

test.describe('Registration Process', () => {
  test.beforeEach(async ({ page }) => {
    await setupAuthenticatedPage(page);
  });

  test('流程页渲染：10 个状态节点、需审核边标记与图例', async ({ page }) => {
    await mockApis(page);
    await page.goto('/registration-process');

    // 10 个状态节点（含无出边的 HISTORICAL 与文档状态 APPLICATION）
    const nodes = page.locator('.status-node');
    await expect(nodes).toHaveCount(10);
    const nodeStatuses = await nodes.evaluateAll((els) => els.map((el) => el.getAttribute('data-status')));
    expect(nodeStatuses.sort()).toEqual([...ALL_STATUSES].sort());

    // 20 条迁移边渲染（Annex D.2）
    await expect(page.locator('.vue-flow__edge')).toHaveCount(20);
    // Annex D.2 纠正后的具体边：新增与移除的断言
    await expect(page.locator('.vue-flow__edge[data-id="INCOMPLETE->HISTORICAL"]')).toHaveCount(1);
    await expect(page.locator('.vue-flow__edge[data-id="APPLICATION->CANDIDATE"]')).toHaveCount(1);
    await expect(page.locator('.vue-flow__edge[data-id="RECORDED->SUPERSEDED"]')).toHaveCount(1);
    // RETIRED 与 HISTORICAL 无任何出边（旧 21 条集合中的边已移除）
    await expect(page.locator('.vue-flow__edge[data-id="RETIRED->HISTORICAL"]')).toHaveCount(0);
    await expect(page.locator('.vue-flow__edge[data-id="SUPERSEDED->HISTORICAL"]')).toHaveCount(0);
    await expect(page.locator('.vue-flow__edge[data-id="INCOMPLETE->RETIRED"]')).toHaveCount(0);

    // 4 条需审核边以红色虚线醒目标记（stroke-dasharray + 红色）
    const edgePaths = page.locator('.vue-flow__edge-path');
    const approvalCount = await edgePaths.evaluateAll((els) =>
      els.filter((el) => {
        const s = (el as SVGPathElement).style;
        return s.strokeDasharray !== 'none' && s.strokeDasharray !== '' && s.stroke.includes('245, 108, 108');
      }).length,
    );
    expect(approvalCount).toBe(4);

    // 所有边带方向箭头：marker 定义存在（灰/红两色），每条 path 引用 marker-end
    const markerDefs = page.locator('.vue-flow__marker defs');
    await expect(markerDefs).toBeAttached();
    const markerHtml = await markerDefs.innerHTML();
    expect(markerHtml).toContain('<marker');
    expect(markerHtml).toContain('rgb(144, 147, 153)');
    expect(markerHtml).toContain('rgb(245, 108, 108)');
    const markerEndCount = await edgePaths.evaluateAll((els) =>
      els.filter((el) => /^url\('#[^']+'\)$/.test((el as SVGPathElement).getAttribute('marker-end') ?? '')).length,
    );
    expect(markerEndCount).toBe(20);

    // 状态分类（11179-6 Table 1）：仅 Historical/Application 为文档状态（灰色系），
    // Superseded/Retired 属生命周期状态（绿色系）——分类不影响迁移规则
    await expect(page.locator('.vue-flow__node[data-id="RETIRED"] .status-node--lifecycle')).toHaveCount(1);
    await expect(page.locator('.vue-flow__node[data-id="SUPERSEDED"] .status-node--lifecycle')).toHaveCount(1);
    await expect(page.locator('.vue-flow__node[data-id="RETIRED"] .status-node--documentation')).toHaveCount(0);
    await expect(page.locator('.vue-flow__node[data-id="HISTORICAL"] .status-node--documentation')).toHaveCount(1);
    await expect(page.locator('.vue-flow__node[data-id="APPLICATION"] .status-node--documentation')).toHaveCount(1);

    // 图例：状态分类、边类型（Recorded 语义说明条位于图例上方，见下一条断言）
    const legend = page.locator('.legend-card');
    await expect(legend).toContainText('生命周期状态');
    await expect(legend).toContainText('文档状态（不推进，可作初始状态）');
    await expect(legend).toContainText('需审核的状态推进');
    // Recorded 前后编辑/审核语义说明条（图例之上）
    await expect(page.locator('[data-test="recorded-note"]'))
      .toContainText('状态推进必须由注册机构批准');
    // 图例位于画布下方
    const boxGraph = await page.locator('.graph-card').boundingBox();
    const boxLegend = await page.locator('.legend-card').boundingBox();
    expect(boxLegend!.y).toBeGreaterThan(boxGraph!.y);
  });

  test('悬停释义：仅选中节点悬停 1 秒后显示，未选中不出现', async ({ page }) => {
    await mockApis(page);
    await page.goto('/registration-process');

    // 未选中节点悬停 1.5 秒：不出现浮窗
    await page.hover('.vue-flow__node[data-id="INCOMPLETE"]');
    await page.waitForTimeout(1500);
    await expect(page.locator('[data-test="tooltip-INCOMPLETE"]')).not.toBeVisible();

    // 选中后悬停：先移开鼠标再进入（确保触发 mouseenter），立即不出现，满 1 秒后出现并含中文释义
    await page.locator('.vue-flow__node[data-id="INCOMPLETE"]').dispatchEvent('click');
    await page.mouse.move(640, 200);
    await page.hover('.vue-flow__node[data-id="INCOMPLETE"]');
    await expect(page.locator('[data-test="tooltip-INCOMPLETE"]')).not.toBeVisible();
    await page.waitForTimeout(1200);
    const tip = page.locator('[data-test="tooltip-INCOMPLETE"]');
    await expect(tip).toBeVisible();
    await expect(tip).toContainText('提交者希望');
    await expect(tip).toContainText('生命周期状态');
    await expect(tip).not.toContainText('aware of the existence of an administered item');
    await expect(tip).not.toContainText('ISO/IEC');

    // 移开立即隐藏
    await page.mouse.move(640, 200);
    await expect(page.locator('[data-test="tooltip-INCOMPLETE"]')).not.toBeVisible();
  });

  test('回归（候选/已记录）：真实鼠标单击选中后原地悬停，浮窗照常出现', async ({ page }) => {
    await mockApis(page);
    await page.goto('/registration-process');
    await page.waitForSelector('.el-loading-mask', { state: 'detached', timeout: 10000 }).catch(() => {});
    await page.waitForTimeout(600);

    for (const id of ['CANDIDATE', 'RECORDED']) {
      // 真实鼠标悬停（未选中，mouseenter 被守卫挡下），随后在不移动鼠标的情况下完成选中——
      // 复现"指针已在节点上时选中"的时序（修复前此场景浮窗永远不出现）
      await page.hover(`.vue-flow__node[data-id="${id}"]`);
      await page.locator(`.vue-flow__node[data-id="${id}"]`).dispatchEvent('click');
      await expect(page.locator('.vue-flow__node.selected')).toHaveCount(1);

      // 原地悬停满 1 秒：浮窗出现（修复前：鼠标不动时浮窗永远不出现）
      await page.waitForTimeout(1300);
      const tip = page.locator(`[data-test="tooltip-${id}"]`);
      await expect(tip).toBeVisible();
      await expect(tip).toContainText(id === 'CANDIDATE' ? '候选' : '已记录');

      // 移开隐藏并取消选中，回归干净状态
      await page.mouse.move(640, 150);
      await expect(tip).not.toBeVisible();
      await page.keyboard.press('Escape');
      await expect(page.locator('.vue-flow__node.selected')).toHaveCount(0);
    }
  });

  test('状态节点：单击仅选中高亮，双击打开释义详情抽屉', async ({ page }) => {
    await mockApis(page);
    await page.goto('/registration-process');

    const node = page.locator('.vue-flow__node[data-id="QUALIFIED"]');

    // 单击：节点高亮，抽屉不打开
    await node.dispatchEvent('click');
    await expect(page.locator('.vue-flow__node[data-id="QUALIFIED"] .status-node--highlighted')).toHaveCount(1);
    await expect(page.locator('[data-test="status-detail"]')).toHaveCount(0);

    // 双击：打开该状态释义详情抽屉，节点保持选中高亮
    await node.dispatchEvent('dblclick');
    const detail = page.locator('[data-test="status-detail"]');
    await expect(detail).toBeVisible();
    await expect(detail).toContainText('已合格');
    await expect(detail).toContainText('QUALIFIED');
    await expect(detail).toContainText('生命周期状态');
    // 释义 + 规范要点：中文界面仅中文
    await expect(detail).toContainText('符合适用的质量要求');
    await expect(detail).toContainText('未获注册机构批准达到 Qualified');
    await expect(detail).not.toContainText('conform to applicable quality requirements');
    await expect(detail).not.toContainText('shall remain at the "Recorded" status');
    await expect(detail).not.toContainText('ISO/IEC');
    await expect(page.locator('.vue-flow__node[data-id="QUALIFIED"] .status-node--highlighted')).toHaveCount(1);
  });

  test('边详情：双击需审核边展示审核配置与功能性说明', async ({ page }) => {
    await mockApis(page);
    await page.goto('/registration-process');

    // 双击 RECORDED→QUALIFIED 需审核边：对边分组元素直接派发 dblclick 事件，
    // 避免 fit-view 缩放/平移时序导致坐标命中抖动
    const edge = page.locator('.vue-flow__edge[data-id="RECORDED->QUALIFIED"]');
    await expect(edge).toBeVisible();
    await edge.dispatchEvent('dblclick');

    const detail = page.locator('[data-test="rule-detail"]');
    await expect(detail).toBeVisible();
    await expect(detail).toContainText('状态推进到 已合格'); // 无 displayName 时的默认操作名
    await expect(detail).toContainText('全部 Registrar'); // 审核人模式
    await expect(detail).toContainText('任一人批准即通过'); // 通过规则
    // 功能性说明：需经注册机构审核批准（页面不再出现标准文档引用）
    await expect(detail).toContainText('按注册流程规范，该状态推进需经审核批准后方可生效');
    await expect(detail).not.toContainText('ISO/IEC');
  });

  test('边选中高亮：单击仅选中联动节点，双击打开抽屉，Esc 取消', async ({ page }) => {
    await mockApis(page);
    await page.goto('/registration-process');

    // 单击 RECORDED→QUALIFIED 边（dispatchEvent 方式，规避 fit-view 坐标命中抖动）
    const edge = page.locator('.vue-flow__edge[data-id="RECORDED->QUALIFIED"]');
    await expect(edge).toBeVisible();
    await edge.dispatchEvent('click');

    // 选中态：.selected class、两端节点高亮；单击不打开抽屉
    await expect(page.locator('.vue-flow__edge.selected')).toHaveCount(1);
    await expect(page.locator('.vue-flow__node[data-id="RECORDED"] .status-node--highlighted')).toHaveCount(1);
    await expect(page.locator('.vue-flow__node[data-id="QUALIFIED"] .status-node--highlighted')).toHaveCount(1);
    await expect(page.locator('[data-test="rule-detail"]')).toHaveCount(0);

    // 选中边为主色加粗高亮
    const selectedPath = page.locator('.vue-flow__edge.selected .vue-flow__edge-path');
    await expect(selectedPath).toHaveCSS('stroke-width', '3px');

    // 双击：抽屉打开且内容对应该边，仍保持选中态
    await edge.dispatchEvent('dblclick');
    await expect(page.locator('[data-test="rule-detail"]')).toBeVisible();
    await expect(page.locator('[data-test="rule-detail"]')).toContainText('状态推进到 已合格');
    await expect(page.locator('.vue-flow__edge.selected')).toHaveCount(1);

    // 单击另一边：高亮切换，抽屉不被主动关闭（仍显示原边内容）
    await page.locator('.vue-flow__edge[data-id="INCOMPLETE->CANDIDATE"]').dispatchEvent('click');
    await expect(page.locator('.vue-flow__edge.selected')).toHaveCount(1);
    await expect(page.locator('.vue-flow__node[data-id="RECORDED"] .status-node--highlighted')).toHaveCount(0);
    await expect(page.locator('.vue-flow__node[data-id="INCOMPLETE"] .status-node--highlighted')).toHaveCount(1);
    await expect(page.locator('[data-test="rule-detail"]')).toContainText('状态推进到 已合格');

    // 双击另一边：抽屉内容切换
    await page.locator('.vue-flow__edge[data-id="INCOMPLETE->CANDIDATE"]').dispatchEvent('dblclick');
    await expect(page.locator('[data-test="rule-detail"]')).toContainText('状态推进到 候选');

    // Esc 取消选中（抽屉随 Esc 关闭并联动清除）
    await page.keyboard.press('Escape');
    await expect(page.locator('.vue-flow__edge.selected')).toHaveCount(0);
    await expect(page.locator('.status-node--highlighted')).toHaveCount(0);
  });

  test('批准路径：推进流程面板批准需审核迁移，进度更新', async ({ page }) => {
    await mockApis(page);
    await page.goto('/administered-items');

    // 列表渲染注册项
    await expect(page.locator('text=DE.00001')).toBeVisible();

    // 打开"推进流程"面板
    await page.locator('[data-test="open-process"]').click();
    const drawer = page.locator('[data-test="process-drawer"]');
    await expect(drawer).toContainText('提交合格审定');
    await expect(drawer).toContainText('0/1'); // 审批进度

    // 点击"批准"
    await drawer.locator('[data-test="approve-btn"]').click();

    // 成功提示（ElMessage）
    await expect(page.locator('.el-message')).toContainText('批准已记录');

    // 面板刷新：进度 1/1，按钮置灰为"已批准"
    await expect(drawer).toContainText('1/1');
    await expect(drawer.locator('[data-test="approved-btn"]')).toBeVisible();
  });

  test('批准请求到达后端：POST /transitions/approve 被调用', async ({ page }) => {
    const requests = await mockApis(page);
    await page.goto('/administered-items');

    await page.locator('[data-test="open-process"]').click();
    await page.locator('[data-test="approve-btn"]').click();
    await expect(page.locator('.el-message')).toContainText('批准已记录');

    expect(
      requests.some((r) => r.includes('POST') && r.includes('/administered-items/item-001/transitions/approve')),
    ).toBe(true);
  });

  test('状态过滤：注册项列表按注册状态过滤并携带 status 参数', async ({ page }) => {
    const requests = await mockApis(page);
    await page.goto('/administered-items');
    await expect(page.locator('text=DE.00001')).toBeVisible();

    // 选择状态过滤：已合格
    await page.locator('[data-test="status-filter"]').click();
    await page.locator('.el-select-dropdown__item', { hasText: '已合格' }).click();

    // 列表请求携带 status=QUALIFIED
    await expect
      .poll(() => requests.some((r) => r.includes('/administered-items?') && r.includes('status=QUALIFIED')))
      .toBe(true);
  });
});
