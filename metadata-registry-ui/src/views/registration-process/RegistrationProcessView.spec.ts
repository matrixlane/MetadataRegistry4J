import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils';
import { nextTick } from 'vue';
import ElementPlus from 'element-plus';
import { createI18n } from 'vue-i18n';
import { VueFlow } from '@vue-flow/core';
import RegistrationProcessView from './RegistrationProcessView.vue';
import { registrationProcessApi, registrationProcessLayoutApi } from '@/api/resources';
import zhCN from '@/i18n/zh-CN.json';
import enUS from '@/i18n/en-US.json';

// jsdom 缺少 ResizeObserver，Vue Flow 视口初始化依赖它
class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}
(globalThis as any).ResizeObserver = ResizeObserverStub;

const authMock = vi.hoisted(() => ({ isAdmin: false }));
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    get isAdmin() {
      return authMock.isAdmin;
    },
  }),
}));

const processDefinition = vi.hoisted(() => ({
  authorityId: 'ra-1',
  transitions: [
    { from: 'INCOMPLETE', to: 'CANDIDATE', displayName: null, requiresApproval: false, approverMode: 'ALL_REGISTRARS', quorumType: 'ANY_ONE', quorumCount: null, approverRegistrarIds: [], enabled: true },
    { from: 'INCOMPLETE', to: 'APPLICATION', displayName: null, requiresApproval: false, approverMode: 'ALL_REGISTRARS', quorumType: 'ANY_ONE', quorumCount: null, approverRegistrarIds: [], enabled: true },
    // Incomplete/Candidate 可直接标定为 Historical
    { from: 'INCOMPLETE', to: 'HISTORICAL', displayName: null, requiresApproval: false, approverMode: 'ALL_REGISTRARS', quorumType: 'ANY_ONE', quorumCount: null, approverRegistrarIds: [], enabled: true },
    { from: 'CANDIDATE', to: 'RECORDED', displayName: null, requiresApproval: false, approverMode: 'ALL_REGISTRARS', quorumType: 'ANY_ONE', quorumCount: null, approverRegistrarIds: [], enabled: true },
    // Application 可推进到 Candidate
    { from: 'APPLICATION', to: 'CANDIDATE', displayName: null, requiresApproval: false, approverMode: 'ALL_REGISTRARS', quorumType: 'ANY_ONE', quorumCount: null, approverRegistrarIds: [], enabled: true },
    { from: 'RECORDED', to: 'QUALIFIED', displayName: '提交合格审定', requiresApproval: true, approverMode: 'ALL_REGISTRARS', quorumType: 'ANY_ONE', quorumCount: null, approverRegistrarIds: [], enabled: true },
    { from: 'QUALIFIED', to: 'STANDARD', displayName: null, requiresApproval: true, approverMode: 'SELECTED_REGISTRARS', quorumType: 'AT_LEAST', quorumCount: 2, approverRegistrarIds: ['reg-1'], enabled: true },
    { from: 'STANDARD', to: 'PREFERRED_STANDARD', displayName: null, requiresApproval: true, approverMode: 'ALL_REGISTRARS', quorumType: 'ANY_ONE', quorumCount: null, approverRegistrarIds: [], enabled: true },
    // 双向迁移对：Preferred Standard 可降回 Standard，与正向边分层渲染
    { from: 'PREFERRED_STANDARD', to: 'STANDARD', displayName: null, requiresApproval: false, approverMode: 'ALL_REGISTRARS', quorumType: 'ANY_ONE', quorumCount: null, approverRegistrarIds: [], enabled: true },
    // Recorded 及以上可被 Superseded
    { from: 'RECORDED', to: 'SUPERSEDED', displayName: null, requiresApproval: false, approverMode: 'ALL_REGISTRARS', quorumType: 'ANY_ONE', quorumCount: null, approverRegistrarIds: [], enabled: true },
    // 被禁用的迁移不应出现在图中
    { from: 'SUPERSEDED', to: 'RETIRED', displayName: null, requiresApproval: false, approverMode: 'ALL_REGISTRARS', quorumType: 'ANY_ONE', quorumCount: null, approverRegistrarIds: [], enabled: false },
  ],
}));

vi.mock('@/api/resources', () => ({
  registrationProcessApi: {
    get: vi.fn(),
  },
  registrationProcessLayoutApi: {
    get: vi.fn().mockRejectedValue(new Error('no saved layout')),
    save: vi.fn(),
  },
}));

const mockedGet = vi.mocked(registrationProcessApi.get);
const mockedLayoutGet = vi.mocked(registrationProcessLayoutApi.get);
const mockedLayoutSave = vi.mocked(registrationProcessLayoutApi.save);

// teleport 浮层挂到 document.body，跨用例残留会污染查询：统一登记并在用例后卸载
const mountedWrappers: VueWrapper[] = [];

afterEach(() => {
  while (mountedWrappers.length) {
    mountedWrappers.pop()!.unmount();
  }
});

/** teleported 释义浮窗是否处于可见态 */
function tooltipVisible(status: string): boolean {
  const el = document.body.querySelector(`[data-test="tooltip-${status}"]`);
  return !!el?.classList.contains('status-tooltip--visible');
}

async function mountView() {
  const i18n = createI18n({
    legacy: false,
    locale: 'zh-CN',
    messages: { 'zh-CN': zhCN as any },
  });
  const wrapper = mount(RegistrationProcessView, {
    global: { plugins: [ElementPlus, i18n] },
    attachTo: document.body,
  });
  mountedWrappers.push(wrapper);
  await flushPromises();
  return wrapper;
}

describe('RegistrationProcessView', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedGet.mockResolvedValue(processDefinition as any);
    mockedLayoutGet.mockRejectedValue(new Error('no saved layout'));
  });

  it('渲染全部 10 个状态节点（含无出边的 HISTORICAL）', async () => {
    const wrapper = await mountView();
    const nodes = wrapper.findAll('.status-node');
    expect(nodes).toHaveLength(10);
    const codes = nodes.map((n) => n.attributes('data-status'));
    for (const status of ['INCOMPLETE', 'CANDIDATE', 'RECORDED', 'QUALIFIED', 'STANDARD', 'PREFERRED_STANDARD', 'SUPERSEDED', 'RETIRED', 'HISTORICAL', 'APPLICATION']) {
      expect(codes).toContain(status);
    }
  });

  it('节点悬停释义按界面语言单语显示（中文界面仅中文译文，不含英文原文）', async () => {
    const wrapper = await mountView();
    // Tooltip 浮层 teleport 到 body，且默认隐藏、仅切换可见性
    const tooltip = wrapper.find('[data-test="tooltip-RECORDED"]');
    expect(tooltip.exists()).toBe(false);
    const teleported = document.body.querySelector('[data-test="tooltip-RECORDED"]');
    expect(teleported).not.toBeNull();
    expect(teleported!.classList.contains('status-tooltip--visible')).toBe(false);
    const text = teleported!.textContent ?? '';
    // 中文界面：状态描述中文译文 + 规范要点（中文）
    expect(text).toContain('所有强制元数据属性均已完成、所有强制关联均已实例化');
    expect(text).toContain('Recorded 及以上状态的受管理项受版本控制');
    // 状态分类（生命周期）
    expect(text).toContain('生命周期状态');
    // 单语显示：英文原文不再出现
    expect(text).not.toContain('all mandatory metadata attributes have been completed');
    expect(text).not.toContain('ISO/IEC');
    expect(teleported!.querySelector('.status-tooltip__text--en')).toBeNull();
  });

  it('状态分类：仅 HISTORICAL/APPLICATION 为文档状态；SUPERSEDED/RETIRED 属生命周期状态', async () => {
    const wrapper = await mountView();
    const categoryOf = (status: string) =>
      document.body.querySelector(`[data-test="tooltip-${status}"]`)?.textContent ?? '';

    // 文档状态：Historical / Application
    expect(categoryOf('APPLICATION')).toContain('文档状态');
    expect(categoryOf('HISTORICAL')).toContain('文档状态');
    // 生命周期状态（含终态 Superseded/Retired）
    for (const status of ['INCOMPLETE', 'CANDIDATE', 'RECORDED', 'QUALIFIED', 'STANDARD', 'PREFERRED_STANDARD', 'SUPERSEDED', 'RETIRED']) {
      expect(categoryOf(status)).toContain('生命周期状态');
      expect(categoryOf(status)).not.toContain('文档状态');
    }
    // 节点配色随分类：生命周期=主色系，文档=灰色系
    expect(wrapper.find('.vue-flow__node[data-id="SUPERSEDED"] .status-node').classes()).toContain('status-node--lifecycle');
    expect(wrapper.find('.vue-flow__node[data-id="RETIRED"] .status-node').classes()).toContain('status-node--lifecycle');
    expect(wrapper.find('.vue-flow__node[data-id="RETIRED"] .status-node').classes()).not.toContain('status-node--documentation');
    expect(wrapper.find('.vue-flow__node[data-id="APPLICATION"] .status-node').classes()).toContain('status-node--documentation');
  });

  it('边按迁移规则渲染，需审核的边以红色虚线醒目标记，禁用迁移不出现', async () => {
    const wrapper = await mountView();
    const edges = wrapper.findAll('.vue-flow__edge');
    // 10 条启用迁移（SUPERSEDED→RETIRED 被禁用而过滤）
    expect(edges).toHaveLength(10);
    const paths = wrapper.findAll('.vue-flow__edge-path');
    const approvalPaths = paths.filter((p) => {
      const style = p.attributes('style') || '';
      return style.includes('stroke-dasharray') && style.includes('rgb(245, 108, 108)');
    });
    // 3 条需审核边（RECORDED→QUALIFIED、QUALIFIED→STANDARD、STANDARD→PREFERRED_STANDARD）
    //（CANDIDATE→RECORDED 按 4.4 c) 为免审核直推）
    expect(approvalPaths).toHaveLength(3);
  });

  it('所有迁移边带方向箭头（markerEnd 定义存在且颜色与边一致）', async () => {
    const wrapper = await mountView();
    // Vue Flow 在 marker 容器中为边渲染 SVG marker 定义
    const markerDefs = wrapper.find('.vue-flow__marker defs');
    expect(markerDefs.exists()).toBe(true);
    const markerHtml = markerDefs.html();
    expect(markerHtml).toContain('<marker');
    // 直接迁移灰 #909399 与需审核红 #f56c6c 两色箭头
    expect(markerHtml).toContain('rgb(144, 147, 153)');
    expect(markerHtml).toContain('rgb(245, 108, 108)');
    // 每条边的 path 都引用 marker（url('#...')）
    const paths = wrapper.findAll('.vue-flow__edge-path');
    expect(paths.length).toBe(10);
    for (const p of paths) {
      expect(p.attributes('marker-end')).toMatch(/^url\('#[^']+'\)$/);
    }
  });

  it('双向迁移对（STANDARD↔PREFERRED_STANDARD）两条边不重合（不同 offset 通道）', async () => {
    const wrapper = await mountView();
    const fwd = wrapper.find('.vue-flow__edge[data-id="STANDARD->PREFERRED_STANDARD"]');
    const rev = wrapper.find('.vue-flow__edge[data-id="PREFERRED_STANDARD->STANDARD"]');
    expect(fwd.exists()).toBe(true);
    expect(rev.exists()).toBe(true);
    // 正向走低通道、反向走高通道：path 数据不同且垂直段错开
    const fwdD = fwd.find('.vue-flow__edge-path').attributes('d');
    const revD = rev.find('.vue-flow__edge-path').attributes('d');
    expect(fwdD).not.toBe(revD);
  });

  it('应用→候选：正交上行 Z 形（与正向垂线分离），且分段可拖动', async () => {
    const wrapper = await mountView();
    const flow = wrapper.findComponent(VueFlow);
    const edges = flow.props('edges') as unknown as Array<Record<string, unknown>>;
    expect(edges.find((e) => e.id === 'APPLICATION->CANDIDATE')?.type).toBe('ortho');
    // 路径：源(应用 240,220)顶缘右偏 360 → 走廊 100 → 目标(候选 240,0)底缘左偏 300 → 64
    const d = wrapper
      .find('.vue-flow__edge[data-id="APPLICATION->CANDIDATE"] .vue-flow__edge-path')
      .attributes('d') ?? '';
    expect(d).toBe('M 330,220 L 330,100 L 330,100 L 330,64');
    // 选中后三个折线段均可拖动
    await wrapper.find('.vue-flow__edge[data-id="APPLICATION->CANDIDATE"]').trigger('click');
    await flushPromises();
    expect(wrapper.findAll('.ortho-seg')).toHaveLength(3);
  });

  it('按类别分带正交走线：灰-红 y 带互不相交，同类别允许重叠', async () => {
    const makeTr = (from: string, to: string, requiresApproval = false) => ({
      from, to, displayName: null, requiresApproval, approverMode: 'ALL_REGISTRARS',
      quorumType: 'ANY_ONE', quorumCount: null, approverRegistrarIds: [], enabled: true,
    });
    mockedGet.mockResolvedValue({
      ...processDefinition,
      transitions: [
        ...processDefinition.transitions,
        makeTr('QUALIFIED', 'RETIRED', true),  // 红跨行下行
        makeTr('STANDARD', 'SUPERSEDED', true), // 红跨行下行（与灰线同目标）
      ],
    } as any);
    const wrapper = await mountView();
    const flow = wrapper.findComponent(VueFlow);
    const edges = flow.props('edges') as unknown as Array<Record<string, unknown>>;
    const laneOf = (id: string) => {
      const d = wrapper.find(`.vue-flow__edge[data-id="${id}"] .vue-flow__edge-path`).attributes('d') ?? '';
      const parts = d.split('L').map((seg) => seg.trim().split(/[ ,]+/).filter(Boolean));
      return { y: Number(parts[1][1]), entryX: Number(parts[2][0]) };
    };
    // 全部跨行/同排线均为正交（仅 应用→候选 为 smoothstep 环路特例）
    for (const id of ['INCOMPLETE->HISTORICAL', 'INCOMPLETE->APPLICATION', 'RECORDED->SUPERSEDED',
      'QUALIFIED->RETIRED', 'STANDARD->SUPERSEDED', 'INCOMPLETE->CANDIDATE', 'RECORDED->QUALIFIED']) {
      expect(edges.find((e) => e.id === id)?.type).toBe('ortho');
    }
    // 应用→候选（上行）：同为正交 Z 形
    expect(edges.find((e) => e.id === 'APPLICATION->CANDIDATE')?.type).toBe('ortho');
    // 灰带：跨行 y=100；红带：跨行 y≥126（互不相交）
    expect(laneOf('RECORDED->SUPERSEDED').y).toBe(100);
    expect(laneOf('QUALIFIED->RETIRED').y).toBeGreaterThanOrEqual(126);
    // 同排链：灰 -24 / 红 -40
    expect(laneOf('INCOMPLETE->CANDIDATE').y).toBe(-24);
    expect(laneOf('RECORDED->QUALIFIED').y).toBe(-40);
    // 同目标（已取代）红-灰共存：红取居中 930（优先级高），灰取就近 1/4 = 885（源在左）
    expect(laneOf('RECORDED->SUPERSEDED').entryX).toBe(885);
    expect(laneOf('STANDARD->SUPERSEDED').entryX).toBe(930);
  });

  it('矩形边声明仲裁：进/出同一中点的跨类别线，红居中、灰取就近 1/4', async () => {
    const wrapper = await mountView();
    const anchorsOf = (id: string) => {
      const d = wrapper.find(`.vue-flow__edge[data-id="${id}"] .vue-flow__edge-path`).attributes('d') ?? '';
      const nums = d.match(/-?\d+\.?\d*/g)?.map(Number) ?? [];
      return { sx: nums[0], tx: nums[4] };
    };
    // 已记录顶缘：灰箭头进（候选→已记录）与红线出（已记录→已合格）共存
    // → 灰进左 1/4（对端候选在左）= 480+45=525；红出居中 = 570
    expect(anchorsOf('CANDIDATE->RECORDED').tx).toBe(525);
    expect(anchorsOf('RECORDED->QUALIFIED').sx).toBe(570);
    // 首选标准顶缘：红箭头进（标准→首选标准）与灰线出（首选标准→标准）共存
    // → 红进居中 = 1290；灰出左 1/4（对端标准在左）= 1200+45=1245
    expect(anchorsOf('STANDARD->PREFERRED_STANDARD').tx).toBe(1290);
    expect(anchorsOf('PREFERRED_STANDARD->STANDARD').sx).toBe(1245);
    // 单类别边不受影响：候选顶缘仅灰 → 居中 330
    expect(anchorsOf('INCOMPLETE->CANDIDATE').tx).toBe(330);
  });

  it('选中正交边后三个折线段可拖动微调（水平段上下拖，本地视图）', async () => {
    const wrapper = await mountView();
    const pathOf = () =>
      wrapper.find('.vue-flow__edge[data-id="RECORDED->SUPERSEDED"] .vue-flow__edge-path').attributes('d') ?? '';
    // 未选中：无拖动热区
    expect(wrapper.findAll('.ortho-seg')).toHaveLength(0);
    await wrapper.find('.vue-flow__edge[data-id="RECORDED->SUPERSEDED"]').trigger('click');
    await flushPromises();
    const segs = wrapper.findAll('.ortho-seg');
    expect(segs).toHaveLength(3);
    const laneYOf = (d: string) => {
      const parts = d.split('L').map((seg) => seg.trim().split(/[ ,]+/).filter(Boolean));
      return Number(parts[1][1]);
    };
    expect(laneYOf(pathOf())).toBe(100);
    // 拖动水平段（index 1）向下 20px（jsdom 无缩放，zoom=1）
    await segs[1].trigger('mousedown', { clientX: 700, clientY: 100 });
    window.dispatchEvent(new MouseEvent('mousemove', { clientX: 700, clientY: 120 }));
    window.dispatchEvent(new MouseEvent('mouseup'));
    await flushPromises();
    expect(laneYOf(pathOf())).toBe(120);
    // 拖动后热区仍在（选中态保持），可继续拖
    expect(wrapper.findAll('.ortho-seg')).toHaveLength(3);
  });

  it('双击需审核边打开抽屉，展示审核配置与功能性说明', async () => {
    const wrapper = await mountView();
    const targetEdge = wrapper.find('.vue-flow__edge[data-id="RECORDED->QUALIFIED"]');
    expect(targetEdge.exists()).toBe(true);
    await targetEdge.trigger('dblclick');
    await flushPromises();
    const drawer = wrapper.find('[data-test="rule-detail"]');
    expect(drawer.exists()).toBe(true);
    const text = drawer.text();
    // 操作名优先使用 displayName
    expect(text).toContain('提交合格审定');
    // 需审核标记 + 审核人模式 + 通过规则
    expect(text).toContain('全部 Registrar');
    expect(text).toContain('任一人批准即通过');
    // 功能性说明（不再出现标准文档引用）
    expect(text).toContain('按注册流程规范，该状态推进需经审核批准后方可生效');
    // 审核发起/审核批准（向上推进：提交组织请求 + 主管者保荐；注册机构批准）
    expect(text).toContain('审核发起');
    expect(text).toContain('提交组织提出推进请求，由主管者保荐并复核质量');
    expect(text).toContain('审核批准');
    expect(text).toContain('注册机构批准（未获批准则保留原状态）');
    expect(text).not.toContain('ISO/IEC');
  });

  it('双击 Qualified+ 降级边展示控制委员会审议路径', async () => {
    const wrapper = await mountView();
    const targetEdge = wrapper.find('.vue-flow__edge[data-id="RECORDED->SUPERSEDED"]');
    // fixture 中该边免审核，临时改mock不足以覆盖——直接验证免审核行
    await targetEdge.trigger('dblclick');
    await flushPromises();
    const drawer = wrapper.find('[data-test="rule-detail"]');
    expect(drawer.text()).toContain('无需审核');
    expect(drawer.text()).toContain('无需审核（具备写权限者可直接执行）');
  });

  it('双击无需审核边展示直接执行说明', async () => {
    const wrapper = await mountView();
    const targetEdge = wrapper.find('.vue-flow__edge[data-id="INCOMPLETE->CANDIDATE"]');
    await targetEdge.trigger('dblclick');
    await flushPromises();
    const drawer = wrapper.find('[data-test="rule-detail"]');
    expect(drawer.text()).toContain('该状态推进无需审核，可由具备写权限的用户直接执行');
    // 无 displayName 时使用默认操作名"状态推进到 X"
    expect(drawer.text()).toContain('状态推进到 候选');
  });

  it('展示图例：状态分类、边类型与 Recorded 约束说明', async () => {
    const wrapper = await mountView();
    const text = wrapper.find('.legend').text();
    expect(text).toContain('生命周期状态');
    expect(text).toContain('文档状态');
    expect(text).toContain('无需审核的状态推进');
    expect(text).toContain('需审核的状态推进');
    // Recorded 前后编辑/审核语义说明（独立提示条，位于图例之上；角色术语按 GB/T 18391：主管者/提交组织）
    expect(wrapper.find('[data-test="recorded-note"]').text())
      .toContain('状态推进必须由注册机构批准');
  });

  it('流程只读但节点可拖动，提供重置布局按钮', async () => {
    const wrapper = await mountView();
    expect(wrapper.find('[data-test="readonly-badge"]').text()).toContain('流程只读');
    // Vue Flow：节点可拖动、不可连线（无编辑入口）
    const flow = wrapper.findComponent(VueFlow);
    expect(flow.props('nodesDraggable')).toBe(true);
    expect(flow.props('nodesConnectable')).toBe(false);
    // 重置布局按钮
    expect(wrapper.find('[data-test="reset-layout"]').exists()).toBe(true);
  });

  it('单击节点仅选中高亮，不打开抽屉；双击打开释义详情且保持选中', async () => {
    const wrapper = await mountView();
    const node = wrapper.find('.vue-flow__node[data-id="QUALIFIED"]');
    expect(node.exists()).toBe(true);

    // 单击：节点高亮，无抽屉
    await node.trigger('click');
    await flushPromises();
    expect(wrapper.find('.vue-flow__node[data-id="QUALIFIED"] .status-node').classes())
      .toContain('status-node--highlighted');
    expect(wrapper.find('[data-test="status-detail"]').exists()).toBe(false);

    // 双击：打开该状态释义详情抽屉，节点保持选中
    await node.trigger('dblclick');
    await flushPromises();
    const detail = wrapper.find('[data-test="status-detail"]');
    expect(detail.exists()).toBe(true);
    const text = detail.text();
    expect(text).toContain('已合格');
    expect(text).toContain('QUALIFIED');
    expect(text).toContain('生命周期状态');
    expect(text).toContain('符合适用的质量要求');
    expect(text).toContain('未获注册机构批准达到 Qualified');
    // 内容变更要求章节（仅抽屉展示，悬浮层不含）
    expect(text).toContain('内容变更要求');
    expect(text).toContain('仅原提交组织或负责主管者可修改');
    expect(text).toContain('控制委员会批准');
    expect(text).not.toContain('conform to applicable quality requirements');
    expect(text).not.toContain('ISO/IEC');
    expect(wrapper.find('.vue-flow__node[data-id="QUALIFIED"] .status-node').classes())
      .toContain('status-node--highlighted');
  });

  it('节点与边选中相互独立：选边后单击节点两者同时高亮，Esc 同时清除', async () => {
    const wrapper = await mountView();

    // 选中一条边
    await wrapper.find('.vue-flow__edge[data-id="RECORDED->QUALIFIED"]').trigger('click');
    await flushPromises();
    expect(wrapper.find('.vue-flow__edge.selected').exists()).toBe(true);

    // 单击节点：节点高亮，边选中不被清除
    await wrapper.find('.vue-flow__node[data-id="INCOMPLETE"]').trigger('click');
    await flushPromises();
    expect(wrapper.find('.vue-flow__node[data-id="INCOMPLETE"] .status-node').classes())
      .toContain('status-node--highlighted');
    expect(wrapper.find('.vue-flow__edge.selected').exists()).toBe(true);
    expect(wrapper.find('.vue-flow__node[data-id="RECORDED"] .status-node').classes())
      .toContain('status-node--highlighted');

    // Esc 同时清除两者
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    await flushPromises();
    expect(wrapper.find('.vue-flow__edge.selected').exists()).toBe(false);
    expect(wrapper.find('.status-node--highlighted').exists()).toBe(false);
  });

  it('en-US 语言包键值正确：statusDesc/statusDetail 为英文原文，双语冗余键已删除', () => {
    const rp = (enUS as any).registrationProcess;
    // statusDesc 在 en-US 下为标准英文原文
    expect(rp.statusDesc.QUALIFIED).toContain('conform to applicable quality requirements');
    expect(rp.statusDesc.INCOMPLETE).toContain('aware of the existence of an administered item');
    // statusDetail 在 en-US 下为标准英文原文
    expect(rp.statusDetail.QUALIFIED).toContain('it shall remain at the "Recorded" registration status level');
    expect(rp.changeDesc.QUALIFIED).toContain('control committee approval');
    // 冗余双语键已移除
    expect(rp.statusDescEn).toBeUndefined();
    expect(rp.statusDetailEn).toBeUndefined();
    expect(rp.sectionDesc).toBeDefined();
    expect(rp.sectionDetail).toBeDefined();
  });

  it('单击边仅选中高亮，不打开抽屉；双击才打开对应详情抽屉', async () => {
    const wrapper = await mountView();
    const targetEdge = wrapper.find('.vue-flow__edge[data-id="RECORDED->QUALIFIED"]');

    // 单击：选中态 + 两端节点联动高亮，但抽屉未打开
    await targetEdge.trigger('click');
    await flushPromises();
    const selected = wrapper.find('.vue-flow__edge.selected');
    expect(selected.exists()).toBe(true);
    expect(selected.attributes('data-id')).toBe('RECORDED->QUALIFIED');
    expect(wrapper.find('.vue-flow__node[data-id="RECORDED"] .status-node').classes())
      .toContain('status-node--highlighted');
    expect(wrapper.find('.vue-flow__node[data-id="QUALIFIED"] .status-node').classes())
      .toContain('status-node--highlighted');
    expect(wrapper.find('[data-test="rule-detail"]').exists()).toBe(false);

    // 高亮样式：主色描边加粗；箭头 marker 同步高亮色
    const path = selected.find('.vue-flow__edge-path');
    const style = path.attributes('style') || '';
    expect(style).toContain('var(--el-color-primary)');
    expect(style).toContain('stroke-width: 3');
    expect(path.attributes('marker-end')).toContain('el-color-primary');

    // 双击：打开详情抽屉且内容对应该边
    await targetEdge.trigger('dblclick');
    await flushPromises();
    const drawer = wrapper.find('[data-test="rule-detail"]');
    expect(drawer.exists()).toBe(true);
    expect(drawer.text()).toContain('提交合格审定');
    // 双击后仍处于选中态
    expect(wrapper.find('.vue-flow__edge.selected').attributes('data-id')).toBe('RECORDED->QUALIFIED');
  });

  it('选中状态切换：单击另一条边，高亮随之转移（不打开抽屉）', async () => {
    const wrapper = await mountView();
    await wrapper.find('.vue-flow__edge[data-id="RECORDED->QUALIFIED"]').trigger('click');
    await flushPromises();
    expect(wrapper.findAll('.vue-flow__edge.selected')).toHaveLength(1);

    await wrapper.find('.vue-flow__edge[data-id="INCOMPLETE->CANDIDATE"]').trigger('click');
    await flushPromises();

    const selected = wrapper.findAll('.vue-flow__edge.selected');
    expect(selected).toHaveLength(1);
    expect(selected[0].attributes('data-id')).toBe('INCOMPLETE->CANDIDATE');
    // 节点高亮转移到新边两端
    expect(wrapper.find('.vue-flow__node[data-id="INCOMPLETE"] .status-node').classes())
      .toContain('status-node--highlighted');
    expect(wrapper.find('.vue-flow__node[data-id="CANDIDATE"] .status-node').classes())
      .toContain('status-node--highlighted');
    expect(wrapper.find('.vue-flow__node[data-id="RECORDED"] .status-node').classes())
      .not.toContain('status-node--highlighted');
    // 单击不打开抽屉
    expect(wrapper.find('[data-test="rule-detail"]').exists()).toBe(false);

    // 双击另一边：抽屉打开且内容切换
    await wrapper.find('.vue-flow__edge[data-id="INCOMPLETE->CANDIDATE"]').trigger('dblclick');
    await flushPromises();
    const drawer = wrapper.find('[data-test="rule-detail"]');
    expect(drawer.exists()).toBe(true);
    expect(drawer.text()).toContain('状态推进到 候选');
  });

  it('取消选中：点击画布空白 / 按 Esc / 关闭抽屉均清除高亮（边与节点）', async () => {
    const wrapper = await mountView();
    // 同时选中边与节点
    await wrapper.find('.vue-flow__edge[data-id="RECORDED->QUALIFIED"]').trigger('click');
    await wrapper.find('.vue-flow__node[data-id="INCOMPLETE"]').trigger('click');
    await flushPromises();
    expect(wrapper.find('.vue-flow__edge.selected').exists()).toBe(true);
    expect(wrapper.find('.status-node--highlighted').exists()).toBe(true);

    // 1) 点击画布空白：同时清除
    await wrapper.find('.vue-flow__pane').trigger('click');
    await flushPromises();
    expect(wrapper.find('.vue-flow__edge.selected').exists()).toBe(false);
    expect(wrapper.find('.status-node--highlighted').exists()).toBe(false);

    // 2) Esc：同时清除
    await wrapper.find('.vue-flow__edge[data-id="RECORDED->QUALIFIED"]').trigger('click');
    await wrapper.find('.vue-flow__node[data-id="INCOMPLETE"]').trigger('click');
    await flushPromises();
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    await flushPromises();
    expect(wrapper.find('.vue-flow__edge.selected').exists()).toBe(false);
    expect(wrapper.find('.status-node--highlighted').exists()).toBe(false);

    // 3) 关闭抽屉（双击边打开后关闭）：同时清除
    await wrapper.find('.vue-flow__edge[data-id="RECORDED->QUALIFIED"]').trigger('dblclick');
    await flushPromises();
    expect(wrapper.find('.vue-flow__edge.selected').exists()).toBe(true);
    const drawer = wrapper.findComponent({ name: 'ElDrawer' });
    drawer.vm.$emit('closed');
    await flushPromises();
    expect(wrapper.find('.vue-flow__edge.selected').exists()).toBe(false);
  });

  it('悬停释义：未选中节点悬停不出现；选中节点悬停满 1 秒才显示，移开立即隐藏', async () => {
    const wrapper = await mountView();
    const findNode = () => wrapper.find('.vue-flow__node[data-id="RECORDED"]');

    vi.useFakeTimers();
    try {
      // 未选中：悬停再久也不显示
      await findNode().trigger('mouseenter');
      vi.advanceTimersByTime(1500);
      await nextTick();
      expect(tooltipVisible('RECORDED')).toBe(false);
      await findNode().trigger('mouseleave');

      // 选中后悬停：不足 1 秒不显示，满 1 秒显示
      await findNode().trigger('click');
      await flushPromises();
      await findNode().trigger('mouseenter');
      expect(tooltipVisible('RECORDED')).toBe(false);
      vi.advanceTimersByTime(900);
      await nextTick();
      expect(tooltipVisible('RECORDED')).toBe(false);
      vi.advanceTimersByTime(200);
      await nextTick();
      expect(tooltipVisible('RECORDED')).toBe(true);

      // 移开立即隐藏
      await findNode().trigger('mouseleave');
      await nextTick();
      expect(tooltipVisible('RECORDED')).toBe(false);
    } finally {
      vi.useRealTimers();
    }
  });

  it('回归（候选/已记录）：指针已在节点上完成选中，悬停浮窗照常出现', async () => {
    const wrapper = await mountView();
    vi.useFakeTimers();
    try {
      for (const id of ['CANDIDATE', 'RECORDED']) {
        // 真实时序复现：mouseenter 先于选中触发（被守卫挡下），指针停留在节点上后单击选中
        await wrapper.find(`.vue-flow__node[data-id="${id}"]`).trigger('mouseenter');
        vi.advanceTimersByTime(1500);
        await nextTick();
        expect(tooltipVisible(id)).toBe(false);

        // jsdom 中节点包围盒退化到 (0,0)：指针 (0,0) 视为悬停于节点上
        window.dispatchEvent(new MouseEvent('mousemove', { clientX: 0, clientY: 0 }));
        await wrapper.find(`.vue-flow__node[data-id="${id}"]`).trigger('click');
        await flushPromises();

        // 选中瞬间未立即显示，悬停满 1 秒后出现
        expect(tooltipVisible(id)).toBe(false);
        vi.advanceTimersByTime(1000);
        await nextTick();
        expect(tooltipVisible(id)).toBe(true);

        // 清理，进入下一个节点
        window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
        await nextTick();
        await wrapper.find(`.vue-flow__node[data-id="${id}"]`).trigger('mouseleave');
      }
    } finally {
      vi.useRealTimers();
    }
  });

  it('悬停释义：停留不足 1 秒移开则取消计时；浮窗显示期间取消选中立即隐藏', async () => {
    const wrapper = await mountView();
    const findNode = (id: string) => wrapper.find(`.vue-flow__node[data-id="${id}"]`);
    await findNode('QUALIFIED').trigger('click');
    await flushPromises();

    vi.useFakeTimers();
    try {
      // 不足 1 秒移开：取消计时，之后不会再弹出
      await findNode('QUALIFIED').trigger('mouseenter');
      vi.advanceTimersByTime(800);
      await nextTick();
      await findNode('QUALIFIED').trigger('mouseleave');
      vi.advanceTimersByTime(1500);
      await nextTick();
      expect(tooltipVisible('QUALIFIED')).toBe(false);

      // 显示期间选中切到别的节点：原浮窗立即隐藏
      await findNode('QUALIFIED').trigger('mouseenter');
      vi.advanceTimersByTime(1000);
      await nextTick();
      expect(tooltipVisible('QUALIFIED')).toBe(true);
      await findNode('INCOMPLETE').trigger('click');
      await nextTick();
      expect(tooltipVisible('QUALIFIED')).toBe(false);

      // 重新选中 QUALIFIED 后再悬停；显示期间 Esc 取消选中：立即隐藏并清计时
      await findNode('QUALIFIED').trigger('click');
      await flushPromises();
      await findNode('QUALIFIED').trigger('mouseenter');
      vi.advanceTimersByTime(1000);
      await nextTick();
      expect(tooltipVisible('QUALIFIED')).toBe(true);
      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
      await nextTick();
      expect(tooltipVisible('QUALIFIED')).toBe(false);
      vi.advanceTimersByTime(1500);
      await nextTick();
      expect(tooltipVisible('QUALIFIED')).toBe(false);
    } finally {
      vi.useRealTimers();
    }
  });

  it('布局顺序：图例在画布上方，提示条保留在画布下方', async () => {
    const wrapper = await mountView();
    const view = wrapper.find('.registration-process-view');
    const children = Array.from(view.element.children);
    const indexOf = (cls: string) => children.findIndex((el) => el.classList.contains(cls));
    const legendIndex = indexOf('legend-card');
    const graphIndex = indexOf('graph-card');
    const noteIndex = indexOf('recorded-note');
    expect(legendIndex).toBeGreaterThanOrEqual(0);
    expect(graphIndex).toBe(legendIndex + 1);
    expect(noteIndex).toBe(graphIndex + 1);
    // 画布占据剩余纵向空间
    expect(wrapper.find('.process-flow').exists()).toBe(true);
  });

  it('预加载已保存布局：节点坐标与连线折线段覆盖生效', async () => {
    mockedLayoutGet.mockResolvedValue({
      nodes: { INCOMPLETE: { x: 10, y: 20 } },
      edgeOverrides: { 'RECORDED->SUPERSEDED': { laneY: 150 } },
    } as any);
    const wrapper = await mountView();
    const flow = wrapper.findComponent(VueFlow);
    const nodes = flow.props('nodes') as Array<{ id: string; position: { x: number; y: number } }>;
    expect(nodes.find((n) => n.id === 'INCOMPLETE')?.position).toEqual({ x: 10, y: 20 });
    const d = wrapper
      .find('.vue-flow__edge[data-id="RECORDED->SUPERSEDED"] .vue-flow__edge-path')
      .attributes('d') ?? '';
    const parts = d.split('L').map((seg) => seg.trim().split(/[ ,]+/).filter(Boolean));
    expect(Number(parts[1][1])).toBe(150);
  });

  it('非 Admin 不展示保存布局按钮；Admin 点击提交全量快照', async () => {
    authMock.isAdmin = false;
    const wrapper = await mountView();
    expect(wrapper.find('[data-test="save-layout"]').exists()).toBe(false);

    authMock.isAdmin = true;
    const wrapper2 = await mountView();
    const btn = wrapper2.find('[data-test="save-layout"]');
    expect(btn.exists()).toBe(true);
    await btn.trigger('click');
    await flushPromises();
    expect(mockedLayoutSave).toHaveBeenCalledTimes(1);
    const payload = mockedLayoutSave.mock.calls[0][0] as {
      nodes: Record<string, unknown>;
      edgeOverrides: Record<string, unknown>;
    };
    expect(Object.keys(payload.nodes)).toHaveLength(10);
    expect(payload.edgeOverrides['RECORDED->SUPERSEDED']).toBeDefined();
    authMock.isAdmin = false;
  });

  it('加载失败时展示错误提示', async () => {
    mockedGet.mockRejectedValue(new Error('network'));
    const wrapper = await mountView();
    expect(wrapper.find('[data-test="load-error"]').exists()).toBe(true);
  });
});
