<template>
  <div class="registration-process-view">
    <div class="header-section">
      <div class="header-left">
        <h2>{{ $t('registrationProcess.title') }}</h2>
        <p class="subtitle">{{ $t('registrationProcess.subtitle') }}</p>
      </div>
      <div class="header-tools">
        <el-tag type="info" effect="plain" class="readonly-badge" data-test="readonly-badge">
          {{ $t('registrationProcess.readonlyBadge') }}
        </el-tag>
        <el-button
          v-if="authStore.isAdmin"
          size="small"
          :icon="Collection"
          data-test="save-layout"
          @click="saveLayout"
        >
          {{ $t('registrationProcess.saveLayout') }}
        </el-button>
        <el-button size="small" :icon="RefreshLeft" data-test="reset-layout" @click="resetLayout">
          {{ $t('registrationProcess.resetLayout') }}
        </el-button>
      </div>
    </div>

    <el-card shadow="never" class="legend-card">
      <div class="legend">
        <span class="legend__title">{{ $t('registrationProcess.legendTitle') }}</span>
        <span class="legend__item">
          <i class="legend__node legend__node--lifecycle" />
          {{ $t('registrationProcess.legendLifecycle') }}
        </span>
        <span class="legend__item">
          <i class="legend__node legend__node--documentation" />
          {{ $t('registrationProcess.legendDocumentation') }}
        </span>
        <span class="legend__item">
          <i class="legend__edge legend__edge--direct" />
          {{ $t('registrationProcess.legendDirectEdge') }}
        </span>
        <span class="legend__item">
          <i class="legend__edge legend__edge--approval" />
          {{ $t('registrationProcess.legendApprovalEdge') }}
        </span>
      </div>
    </el-card>

    <el-card shadow="never" class="graph-card" v-loading="loading">
      <el-alert
        v-if="loadError"
        type="error"
        :closable="false"
        show-icon
        :title="$t('registrationProcess.loadError')"
        data-test="load-error"
      />
      <VueFlow
        v-if="nodes.length"
        :key="layoutVersion"
        :nodes="nodes"
        :edges="edges"
        :nodes-draggable="true"
        :nodes-connectable="false"
        :elements-selectable="true"
        :pan-on-drag="true"
        :zoom-on-scroll="true"
        :min-zoom="0.2"
        :max-zoom="1.5"
        fit-view-on-init
        class="process-flow"
        data-test="process-flow"
        @edge-click="onEdgeClick"
        @edge-double-click="onEdgeDoubleClick"
        @edges-change="onEdgesChange"
        @nodes-change="onNodesChange"
        @pane-click="clearSelection"
        @node-click="onNodeClick"
        @node-double-click="onNodeDoubleClick"
        @node-mouse-enter="onNodeMouseEnter"
        @node-mouse-leave="onNodeMouseLeave"
      >
        <template #node-status="nodeProps">
          <StatusFlowNode :data="nodeProps.data" />
        </template>
        <template #edge-ortho="edgeProps">
          <!-- 正交走廊边：自定义 path 渲染（按类别分带，灰-红不重叠） -->
          <BaseEdge
            :id="edgeProps.id"
            :path="ORTHO_PATHS.get(edgeProps.id) ?? ''"
            :marker-end="edgeProps.markerEnd"
            :style="edgeProps.style"
          />
          <!-- 选中后边线可调：三个折线段各附透明热区，水平段上下拖、垂直段左右拖（本地微调） -->
          <path
            v-for="seg in edgeProps.selected ? orthoSegments(edgeProps.id) : []"
            :key="seg.key"
            :d="seg.d"
            class="ortho-seg"
            :class="seg.kind === 'v' ? 'ortho-seg--v' : 'ortho-seg--h'"
            @mousedown="onSegMouseDown($event, edgeProps.id, Number(seg.key))"
          />
        </template>
      </VueFlow>
      <el-empty v-else-if="!loading && !loadError" :description="$t('registrationProcess.empty')" />
    </el-card>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      :title="$t('registrationProcess.legendRecordedNote')"
      class="recorded-note"
      data-test="recorded-note"
    />

    <!-- 悬停释义浮层：teleport 到 body，fixed 定位，不被画布 overflow 裁剪；
         内容常驻 DOM（仅切换可见性），便于 e2e/vitest 断言 -->
    <Teleport to="body">
      <div
        v-for="status in ALL_STATUSES"
        :key="status"
        :data-test="`tooltip-${status}`"
        class="status-tooltip"
        :class="{
          'status-tooltip--visible': visibleTooltipStatus === status,
          'status-tooltip--above': tooltipPlacement === 'top',
        }"
        :style="tooltipStyle"
      >
        <div class="status-tooltip__title">
          {{ $t('registrationStatus.' + status) }} · {{ status }}
        </div>
        <div class="status-tooltip__category">
          {{ $t('registrationProcess.tooltipCategory') }}：
          {{ categoryLabelOf(status) }}
        </div>
        <div class="status-tooltip__section">{{ $t('registrationProcess.sectionDesc') }}</div>
        <p class="status-tooltip__text">{{ $t('registrationProcess.statusDesc.' + status) }}</p>
        <div class="status-tooltip__section">{{ $t('registrationProcess.sectionDetail') }}</div>
        <p class="status-tooltip__text">{{ $t('registrationProcess.statusDetail.' + status) }}</p>
      </div>
    </Teleport>

    <!-- 抽屉：边详情 / 状态释义两种模式 -->
    <el-drawer
      v-model="drawerVisible"
      :title="drawerMode === 'status' ? $t('registrationProcess.statusDrawerTitle') : $t('registrationProcess.drawerTitle')"
      size="440px"
      data-test="rule-drawer"
      @closed="clearSelection"
    >
      <div v-if="drawerMode === 'edge' && selectedRule" class="rule-detail" data-test="rule-detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item :label="$t('registrationProcess.drawerFrom')">
            <el-tag size="small">{{ $t('registrationStatus.' + selectedRule.from) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('registrationProcess.drawerTo')">
            <el-tag size="small" type="success">{{ $t('registrationStatus.' + selectedRule.to) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('registrationProcess.drawerOperation')">
            {{
              selectedRule.displayName ||
              $t('registrationProcess.defaultOperationName', {
                status: $t('registrationStatus.' + selectedRule.to),
              })
            }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('registrationProcess.drawerApprovalRequired')">
            <el-tag v-if="selectedRule.requiresApproval" type="warning" size="small">
              {{ $t('registrationProcess.yes') }}
            </el-tag>
            <el-tag v-else type="info" size="small">{{ $t('registrationProcess.no') }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('registrationProcess.drawerReviewInitiator')">
            <template v-if="selectedRule.requiresApproval && reviewKindOf(selectedRule) === 'up'">
              {{ $t('registrationProcess.reviewInitiatorUp') }}
            </template>
            <template v-else-if="selectedRule.requiresApproval">
              {{ $t('registrationProcess.reviewInitiatorDown') }}
            </template>
            <template v-else>{{ $t('registrationProcess.reviewNone') }}</template>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('registrationProcess.drawerReviewApprover')">
            <template v-if="selectedRule.requiresApproval && reviewKindOf(selectedRule) === 'up'">
              {{ $t('registrationProcess.reviewApproverUp') }}
            </template>
            <template v-else-if="selectedRule.requiresApproval">
              {{ $t('registrationProcess.reviewApproverDown') }}
            </template>
            <template v-else>{{ $t('registrationProcess.reviewDirectApprover') }}</template>
          </el-descriptions-item>
          <template v-if="selectedRule.requiresApproval">
            <el-descriptions-item :label="$t('registrationProcess.drawerApproverMode')">
              {{
                selectedRule.approverMode === 'SELECTED_REGISTRARS'
                  ? $t('registrationProcess.approverModeSelected')
                  : $t('registrationProcess.approverModeAll')
              }}
            </el-descriptions-item>
            <el-descriptions-item :label="$t('registrationProcess.drawerQuorum')">
              {{ quorumLabel(selectedRule) }}
            </el-descriptions-item>
          </template>
        </el-descriptions>
        <el-alert
          class="rule-detail__basis"
          :type="selectedRule.requiresApproval ? 'warning' : 'info'"
          :closable="false"
          show-icon
          :title="$t('registrationProcess.ruleNoteTitle')"
          :description="
            selectedRule.requiresApproval
              ? $t('registrationProcess.ruleNoteApproval')
              : $t('registrationProcess.ruleNoteDirect')
          "
        />
      </div>

      <div v-else-if="drawerMode === 'status' && selectedStatus" class="status-detail" data-test="status-detail">
        <div class="status-detail__head">
          <el-tag size="small" :type="categoryOf(selectedStatus) === 'lifecycle' ? 'primary' : 'info'">
            {{ $t('registrationStatus.' + selectedStatus) }}
          </el-tag>
          <span class="status-detail__code">{{ selectedStatus }}</span>
          <el-tag size="small" :type="categoryOf(selectedStatus) === 'lifecycle' ? 'success' : 'warning'">
            {{ categoryLabelOf(selectedStatus) }}
          </el-tag>
        </div>
        <div class="status-detail__section">{{ $t('registrationProcess.sectionDesc') }}</div>
        <p class="status-detail__text">{{ $t('registrationProcess.statusDesc.' + selectedStatus) }}</p>
        <div class="status-detail__section">{{ $t('registrationProcess.sectionDetail') }}</div>
        <p class="status-detail__text">{{ $t('registrationProcess.statusDetail.' + selectedStatus) }}</p>
        <div class="status-detail__section">{{ $t('registrationProcess.sectionChange') }}</div>
        <p class="status-detail__text">{{ $t('registrationProcess.changeDesc.' + selectedStatus) }}</p>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, provide, onMounted, onBeforeUnmount } from 'vue';
import { VueFlow, MarkerType, BaseEdge } from '@vue-flow/core';
import type { Node, Edge, NodeMouseEvent, EdgeChange } from '@vue-flow/core';
import '@vue-flow/core/dist/style.css';
import '@vue-flow/core/dist/theme-default.css';
import { Collection, RefreshLeft } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import { ElMessage } from 'element-plus';
import { registrationProcessApi, registrationProcessLayoutApi } from '@/api/resources';
import type { RegistrationProcessLayout } from '@/types/common';
import { useAuthStore } from '@/stores/auth';
import type { RegistrationProcessDefinition, RegistrationProcessTransition } from '@/types/common';
import StatusFlowNode from './StatusFlowNode.vue';

const { t } = useI18n();
const authStore = useAuthStore();

/** 生命周期状态（共 8 个；含终态 Superseded/Retired——分类不影响迁移规则） */
const LIFECYCLE_STATUSES = [
  'INCOMPLETE',
  'CANDIDATE',
  'RECORDED',
  'QUALIFIED',
  'STANDARD',
  'PREFERRED_STANDARD',
  'SUPERSEDED',
  'RETIRED',
];

/** 文档状态（仅 Historical 与 Application，不推进、可作初始状态） */
const DOCUMENTATION_STATUSES = ['HISTORICAL', 'APPLICATION'];

const ALL_STATUSES = [...LIFECYCLE_STATUSES, ...DOCUMENTATION_STATUSES];

/**
 * 只读流程图布局（迁移集）：
 * 主链横向；HISTORICAL / APPLICATION 侧链挂在 Incomplete/Candidate 下方；
 * SUPERSEDED 接收 Recorded 及以上的取代迁移；RETIRED 收尾（无出边）。
 */
const NODE_POSITIONS: Record<string, { x: number; y: number }> = {
  INCOMPLETE: { x: 0, y: 0 },
  CANDIDATE: { x: 240, y: 0 },
  RECORDED: { x: 480, y: 0 },
  QUALIFIED: { x: 720, y: 0 },
  STANDARD: { x: 960, y: 0 },
  PREFERRED_STANDARD: { x: 1200, y: 0 },
  HISTORICAL: { x: 0, y: 220 },
  APPLICATION: { x: 240, y: 220 },
  SUPERSEDED: { x: 840, y: 220 },
  RETIRED: { x: 1200, y: 220 },
};

const loading = ref(false);
const loadError = ref(false);
const definition = ref<RegistrationProcessDefinition | null>(null);
const nodes = ref<Node[]>([]);

/** 已保存的画布布局（启动预加载；合成优先级：内置默认 < 已保存布局 < 会话拖动） */
const savedLayout = ref<RegistrationProcessLayout | null>(null);

function isNum(v: unknown): v is number {
  return typeof v === 'number' && Number.isFinite(v);
}

/** 校验并清洗服务端布局：丢弃非法坐标项（损坏的手工编辑不导致白屏） */
function sanitizeLayout(raw: unknown): RegistrationProcessLayout | null {
  if (!raw || typeof raw !== 'object') return null;
  const out: RegistrationProcessLayout = {};
  const nodesRaw = (raw as RegistrationProcessLayout).nodes;
  if (nodesRaw && typeof nodesRaw === 'object') {
    const nodes: RegistrationProcessLayout['nodes'] = {};
    for (const [k, v] of Object.entries(nodesRaw)) {
      if (v && isNum(v.x) && isNum(v.y)) nodes[k] = { x: v.x, y: v.y };
    }
    out.nodes = nodes;
  }
  const edgesRaw = (raw as RegistrationProcessLayout).edgeOverrides;
  if (edgesRaw && typeof edgesRaw === 'object') {
    const edgeOverrides: RegistrationProcessLayout['edgeOverrides'] = {};
    for (const [k, v] of Object.entries(edgesRaw)) {
      if (!v || typeof v !== 'object') continue;
      const e = v as NonNullable<RegistrationProcessLayout['edgeOverrides']>[string];
      const entry: NonNullable<RegistrationProcessLayout['edgeOverrides']>[string] = {};
      if (isNum(e.laneY)) entry.laneY = e.laneY;
      if (isNum(e.sx)) entry.sx = e.sx;
      if (isNum(e.tx)) entry.tx = e.tx;
      if (entry.laneY !== undefined || entry.sx !== undefined || entry.tx !== undefined) {
        edgeOverrides[k] = entry;
      }
    }
    out.edgeOverrides = edgeOverrides;
  }
  return out.nodes || out.edgeOverrides ? out : null;
}
/** 重置布局时递增，强制 Vue Flow 重建并 fit-view */
const layoutVersion = ref(0);

const drawerVisible = ref(false);
const drawerMode = ref<'edge' | 'status'>('edge');
const selectedRule = ref<RegistrationProcessTransition | null>(null);
const selectedStatus = ref<string | null>(null);

// ── 悬停释义浮层：选中节点悬停满 1 秒后显示 ──────────────────────────────

const HOVER_DELAY_MS = 1000;

/** 计时中的节点（选中节点悬停启动计时，到点显示浮窗） */
const hoverNodeId = ref<string | null>(null);
/** 正在显示的浮窗对应状态 */
const visibleTooltipStatus = ref<string | null>(null);
const tooltipPos = ref<{ x: number; y: number }>({ x: 0, y: 0 });
const tooltipPlacement = ref<'top' | 'bottom'>('bottom');
let hoverTimer: ReturnType<typeof setTimeout> | null = null;

/** 最近一次指针位置（window mousemove 跟踪）：用于"指针已在节点上时完成选中"的场景 */
const lastPointer = ref<{ x: number; y: number }>({ x: -1, y: -1 });

const tooltipStyle = computed(() => ({ left: `${tooltipPos.value.x}px`, top: `${tooltipPos.value.y}px` }));

function clearHoverTimer() {
  if (hoverTimer) {
    clearTimeout(hoverTimer);
    hoverTimer = null;
  }
  hoverNodeId.value = null;
}

function hideTooltip() {
  visibleTooltipStatus.value = null;
}

function positionTooltip(rect: DOMRect) {
  // 视口下方空间不足时改为节点上方弹出
  tooltipPlacement.value = rect.bottom + 420 > window.innerHeight ? 'top' : 'bottom';
  tooltipPos.value =
    tooltipPlacement.value === 'top'
      ? { x: rect.left + rect.width / 2, y: rect.top - 8 }
      : { x: rect.left + rect.width / 2, y: rect.bottom + 8 };
}

/** 启动悬停计时（前提是节点已选中） */
function armHoverTimer(nodeId: string, rect: DOMRect) {
  clearHoverTimer();
  hideTooltip();
  positionTooltip(rect);
  hoverNodeId.value = nodeId;
  hoverTimer = setTimeout(() => {
    visibleTooltipStatus.value = nodeId;
    hoverTimer = null;
  }, HOVER_DELAY_MS);
}

function onNodeMouseEnter({ event, node }: NodeMouseEvent) {
  // 未选中节点悬停不显示
  if (node.id !== selectedNodeId.value) return;
  const wrapper = (event.target as HTMLElement | null)?.closest?.('.vue-flow__node');
  const rect = wrapper?.getBoundingClientRect();
  if (!rect) return;
  armHoverTimer(node.id, rect);
}

function onNodeMouseLeave() {
  clearHoverTimer();
  hideTooltip();
}

/** 选中时已悬停场景：mouseenter 先于单击选中触发会被守卫挡下，
 *  选中变化后若指针正位于该节点矩形内，立即补启动计时（否则鼠标不动将永远无浮窗） */
function armHoverIfPointerOver(nodeId: string) {
  const el = document.querySelector(`.vue-flow__node[data-id="${nodeId}"]`);
  if (!el) return;
  const rect = el.getBoundingClientRect();
  const { x, y } = lastPointer.value;
  if (x < rect.left || x > rect.right || y < rect.top || y > rect.bottom) return;
  armHoverTimer(nodeId, rect);
}

function onWindowMouseMove(event: MouseEvent) {
  lastPointer.value = { x: event.clientX, y: event.clientY };
}

// ── 图数据 ──────────────────────────────────────────────────────────────

function categoryOf(status: string): 'lifecycle' | 'documentation' {
  return LIFECYCLE_STATUSES.includes(status) ? 'lifecycle' : 'documentation';
}

function categoryLabelOf(status: string): string {
  return categoryOf(status) === 'documentation'
    ? t('registrationProcess.categoryDocumentation')
    : t('registrationProcess.categoryLifecycle');
}

/** 需审核推进的分类：向上推进（目标 Qualified/Standard/Preferred Standard，4.4 d)）或
 *  Qualified+ 降级（目标 Retired/Superseded，D.4.2/D.4.3）——决定"审核发起/审核批准"文案 */
const UPWARD_TARGETS = new Set(['QUALIFIED', 'STANDARD', 'PREFERRED_STANDARD']);
const DOWNGRADE_TARGETS = new Set(['RETIRED', 'SUPERSEDED']);

function reviewKindOf(rule: RegistrationProcessTransition): 'up' | 'down' {
  return UPWARD_TARGETS.has(rule.to) ? 'up' : DOWNGRADE_TARGETS.has(rule.to) ? 'down' : 'up';
}

function buildNodes(): Node[] {
  const statuses = new Set<string>(ALL_STATUSES);
  (definition.value?.transitions ?? [])
    .filter((tr) => tr.enabled)
    .forEach((tr) => {
      statuses.add(tr.from);
      statuses.add(tr.to);
    });
  return [...statuses].map((status) => ({
    id: status,
    type: 'status',
    position: baselinePos()[status] ?? { x: 0, y: 0 },
    data: { status, category: categoryOf(status) },
    style: { width: '180px', height: '64px' },
    draggable: true,
    connectable: false,
    selectable: true,
  }));
}

/** 判断双向迁移对（两个方向均启用）：默认曲线两方向路径重合，需以不同 offset 分层 */
function hasReverseTransition(tr: RegistrationProcessTransition, all: RegistrationProcessTransition[]): boolean {
  return all.some((o) => o.enabled && o.from === tr.to && o.to === tr.from);
}

const NODE_W = 180;
const NODE_H = 64;

/** 基线布局：内置默认叠加坡动前已保存的布局（重置布局回到这里） */
function baselinePos(): Record<string, { x: number; y: number }> {
  return { ...NODE_POSITIONS, ...(savedLayout.value?.nodes ?? {}) };
}

/** 实时节点位置：基线叠加坡动后的位置（拖动节点后正交路径随之重建，箭头和矩形保持连接） */
function livePos(): Record<string, { x: number; y: number }> {
  const pos = baselinePos();
  for (const n of nodes.value) {
    pos[n.id] = { ...n.position };
  }
  return pos;
}

/**
 * 按类别分带的正交走廊（唯一约束：灰-红不重合；同类别允许重叠）。
 * 类别使用互不相交的 y 带，从构造上杜绝跨类别重合：
 *   顶行同排线——灰 -24 / 红 -40（行上方）；底行同排线——灰 300 / 红 330（行下方）；
 *   跨行下行线——灰 100 / 红按目标 126 起（行间隙内）。
 * 同排线落点 x 按"出左偏(-24)、入右偏(+24)"错位，避免同一矩形顶缘的灰红垂直段重合。
 */
const GRAY_UP_LANE_Y = -24;
const RED_UP_LANE_Y = -40;
const GRAY_BOTTOM_LANE_Y = 300;
const RED_BOTTOM_LANE_Y = 330;
const GRAY_DOWN_LANE_Y = 100;
const RED_DOWN_LANE_BASE = 126;
const RED_DOWN_LANE_GAP = 36;
/** 结束点取 1/4 处时的偏移（矩形宽 180 的一半居中 = 45/135） */
const QUARTER_X = 45;

/** 正交走廊参数（含源/目标锚点；拖动节点后基于实时位置重建） */
interface OrthoPlanEntry {
  laneY: number;
  sx: number;
  sy: number;
  tx: number;
  ty: number;
}

function buildOrthoPlan(
  enabled: RegistrationProcessTransition[],
  pos: Record<string, { x: number; y: number }>,
): Map<string, OrthoPlanEntry> {
  const plan = new Map<string, OrthoPlanEntry>();
  // 矩形边声明：每条边在其使用的源/目标边（顶/底）登记类别（进来的箭头与出去的线都算）。
  // 某边中点的声明红-灰共存时：红色居中（优先级高），灰色取就近 1/4；仅单类别时居中。
  const sideClaims = new Map<string, Set<'gray' | 'red'>>();
  const claim = (node: string, side: 'top' | 'bottom', cat: 'gray' | 'red') => {
    const key = `${node}:${side}`;
    const set = sideClaims.get(key) ?? new Set<'gray' | 'red'>();
    set.add(cat);
    sideClaims.set(key, set);
  };
  for (const o of enabled) {
    const fp = pos[o.from];
    const tp = pos[o.to];
    if (!fp || !tp || o.from === o.to) continue;
    const cat = o.requiresApproval ? 'red' : 'gray';
    if (fp.y < tp.y) {
      claim(o.from, 'bottom', cat);
      claim(o.to, 'top', cat);
    } else if (fp.y === tp.y) {
      const side = fp.y < 200 ? 'top' : 'bottom';
      claim(o.from, side, cat);
      claim(o.to, side, cat);
    } else {
      claim(o.from, 'top', cat);
      claim(o.to, 'bottom', cat);
    }
  }
  /** 锚点 x：红优先居中；该边声明红-灰共存时灰色取就近 1/4（依据对端中心位置）；仅单类别时居中 */
  const anchorX = (node: string, side: 'top' | 'bottom', cat: 'gray' | 'red', otherCenterX: number): number => {
    const center = pos[node].x + 90;
    const claims = sideClaims.get(`${node}:${side}`);
    if (cat === 'red' || !claims?.has('red') || !claims.has('gray')) return center;
    return pos[node].x + (otherCenterX < center ? QUARTER_X : NODE_W - QUARTER_X);
  };

  // 跨行下行红线按目标分配不重叠的红带车道（红-红同带允许，仅须与灰带分离）
  const redTargets = [...new Set(
    enabled
      .filter((o) => o.requiresApproval && o.from !== o.to
        && (pos[o.from]?.y ?? 0) < (pos[o.to]?.y ?? 0)
        && !hasReverseTransition(o, enabled))
      .map((o) => o.to),
  )].sort((a, b) => (pos[a]?.x ?? 0) - (pos[b]?.x ?? 0));
  const redLaneOf = new Map(redTargets.map((t, i) => [t, RED_DOWN_LANE_BASE + i * RED_DOWN_LANE_GAP]));

  for (const tr of enabled) {
    const fp = pos[tr.from];
    const tp = pos[tr.to];
    if (!fp || !tp || tr.from === tr.to) continue;
    if (fp.y < tp.y) {
      // 跨行下行：出发点 = 源矩形居中；结束点 = endX 规则
      if (tr.requiresApproval) {
        plan.set(`${tr.from}->${tr.to}`, {
          laneY: redLaneOf.get(tr.to) ?? RED_DOWN_LANE_BASE,
          sx: anchorX(tr.from, 'bottom', 'red', tp.x + 90),
          sy: fp.y + NODE_H,
          tx: anchorX(tr.to, 'top', 'red', fp.x + 90),
          ty: tp.y,
        });
      } else {
        plan.set(`${tr.from}->${tr.to}`, {
          laneY: GRAY_DOWN_LANE_Y,
          sx: anchorX(tr.from, 'bottom', 'gray', tp.x + 90),
          sy: fp.y + NODE_H,
          tx: anchorX(tr.to, 'top', 'gray', fp.x + 90),
          ty: tp.y,
        });
      }
    } else if (fp.y === tp.y) {
      // 同排（顶行链 / 双向对 / 底行）：出发点 = 源边居中；结束点 = endX 规则
      const top = fp.y < 200;
      const laneY = tr.requiresApproval
        ? (top ? RED_UP_LANE_Y : RED_BOTTOM_LANE_Y)
        : (top ? GRAY_UP_LANE_Y : GRAY_BOTTOM_LANE_Y);
      plan.set(`${tr.from}->${tr.to}`, {
        laneY,
        sx: anchorX(tr.from, top ? 'top' : 'bottom', tr.requiresApproval ? 'red' : 'gray', tp.x + 90),
        sy: top ? fp.y : fp.y + NODE_H,
        tx: anchorX(tr.to, top ? 'top' : 'bottom', tr.requiresApproval ? 'red' : 'gray', fp.x + 90),
        ty: top ? tp.y : tp.y + NODE_H,
      });
    } else if (fp.y > tp.y) {
      // 上行（应用→候选）：出发点 = 源矩形居中；结束点 = endX 规则（候选仅灰线 → 居中）
      if (!tr.requiresApproval) {
        plan.set(`${tr.from}->${tr.to}`, {
          laneY: GRAY_DOWN_LANE_Y,
          sx: anchorX(tr.from, 'top', 'gray', tp.x + 90),
          sy: fp.y,
          tx: anchorX(tr.to, 'bottom', 'gray', fp.x + 90),
          ty: tp.y + NODE_H,
        });
      }
    }
  }
  return plan;
}

/** 正交路径：源锚点 → 垂直入走廊 → 水平走廊 → 垂直入目标锚点 */
function orthoPath(entry: OrthoPlanEntry): string {
  return `M ${entry.sx},${entry.sy} L ${entry.sx},${entry.laneY} L ${entry.tx},${entry.laneY} L ${entry.tx},${entry.ty}`;
}

/** 正交边的自定义 path 寄存（edge.data 须保持迁移规则本体，path 经 id 查表供 edge-ortho 插槽渲染） */
const ORTHO_PATHS = new Map<string, string>();

/** 初始化的线段微调默认值（"保存当前布局为初始状态"流程写回的绝对画布坐标；用户拖动覆盖优先） */
const DEFAULT_EDGE_OVERRIDES: Record<string, { laneY?: number; sx?: number; tx?: number }> = {};

/** 正交线段的拖动微调覆盖（本地视图，不保存；重置布局时清除）：laneY/sx/tx 绝对画布坐标 */
const edgeOverrides = new Map<string, { laneY?: number; sx?: number; tx?: number }>();

/** 画布缩放系数：viewport transform scale（视图组件在 VueFlow 之外，无法 useVueFlow，从 DOM 读取） */
function canvasZoom(): number {
  const vp = document.querySelector('.vue-flow__viewport') as HTMLElement | null;
  const m = /scale\((-?[\d.]+)\)/.exec(vp?.style.transform ?? '');
  return m ? Number(m[1]) : 1;
}

interface OrthoSegDrag {
  id: string;
  segIndex: number;
  base: { laneY: number; sx: number; tx: number };
  startX: number;
  startY: number;
}
let segDrag: OrthoSegDrag | null = null;

/** 选中边的正交折线段（首垂直段/水平走廊段/末垂直段），供拖动热区渲染 */
function orthoSegments(id: string): Array<{ key: string; kind: 'v' | 'h'; d: string }> {
  const path = ORTHO_PATHS.get(id);
  if (!path) return [];
  const nums = path.match(/-?\d+\.?\d*/g)?.map(Number) ?? [];
  const pts: Array<[number, number]> = [];
  for (let i = 0; i + 1 < nums.length; i += 2) pts.push([nums[i], nums[i + 1]]);
  const segs: Array<{ key: string; kind: 'v' | 'h'; d: string }> = [];
  for (let i = 0; i + 1 < pts.length; i++) {
    const [x1, y1] = pts[i];
    const [x2, y2] = pts[i + 1];
    segs.push({
      key: String(i),
      kind: x1 === x2 ? 'v' : 'h',
      d: `M ${x1},${y1} L ${x2},${y2}`,
    });
  }
  return segs;
}

function onSegMouseDown(event: MouseEvent, id: string, segIndex: number) {
  event.stopPropagation();
  event.preventDefault();
  const path = ORTHO_PATHS.get(id);
  if (!path) return;
  const nums = path.match(/-?\d+\.?\d*/g)?.map(Number) ?? [];
  segDrag = {
    id,
    segIndex,
    base: { sx: nums[0], laneY: nums[3], tx: nums[4] },
    startX: event.clientX,
    startY: event.clientY,
  };
  window.addEventListener('mousemove', onSegMouseMove);
  window.addEventListener('mouseup', onSegMouseUp);
}

function onSegMouseMove(event: MouseEvent) {
  if (!segDrag) return;
  const dx = (event.clientX - segDrag.startX) / canvasZoom();
  const dy = (event.clientY - segDrag.startY) / canvasZoom();
  const o = edgeOverrides.get(segDrag.id) ?? {};
  if (segDrag.segIndex === 1) {
    o.laneY = segDrag.base.laneY + dy;
  } else if (segDrag.segIndex === 0) {
    o.sx = segDrag.base.sx + dx;
  } else if (segDrag.segIndex === 2) {
    o.tx = segDrag.base.tx + dx;
  } else {
    return;
  }
  edgeOverrides.set(segDrag.id, o);
  edges.value = buildEdges();
}

function onSegMouseUp() {
  segDrag = null;
  window.removeEventListener('mousemove', onSegMouseMove);
  window.removeEventListener('mouseup', onSegMouseUp);
}



function edgeOffset(tr: RegistrationProcessTransition, enabled: RegistrationProcessTransition[]): number {
  // 双向对：正向（from<code）走低通道，反向走高通道，避免两方向线重合
  if (hasReverseTransition(tr, enabled)) {
    return tr.from < tr.to ? 16 : 32;
  }
  // 同行单线（跨行线已由正交走廊接管）：小幅 offset
  return 10;
}

// ── 边/节点选中态（两者相互独立，各自单一选中） ───────────────────────────

const selectedEdgeId = ref<string | null>(null);
const selectedNodeId = ref<string | null>(null);

// 选中变化（Esc/点空白/切换选中/关闭抽屉）时立即隐藏浮窗并清计时
watch(selectedNodeId, (id) => {
  clearHoverTimer();
  hideTooltip();
  // 指针已悬停在该节点上（mouseenter 先于选中触发被守卫挡下）→ 补启动计时
  if (id) armHoverIfPointerOver(id);
});

function buildEdges(): Edge[] {
  const enabled = (definition.value?.transitions ?? []).filter((tr) => tr.enabled);
  const pos = livePos();
  const orthoPlan = buildOrthoPlan(enabled, pos);
  return enabled.map((tr) => {
    const id = `${tr.from}->${tr.to}`;
    const color = tr.requiresApproval ? '#f56c6c' : '#909399';
    const selected = selectedEdgeId.value === id;
    // 灰线跨行下行：正交走廊（灰-红分离）；红线跨行下行：smoothstep（红-红允许重叠）；其余：smoothstep
    const planEntry = orthoPlan.get(id);
    const offset = planEntry ? 0 : edgeOffset(tr, enabled);
    const sideRoute = {};
    // 选中高亮：主题主色加粗，箭头颜色同步高亮（marker 按颜色生成定义）
    const stroke = selected ? 'var(--el-color-primary)' : color;
    const base = {
      id,
      source: tr.from,
      target: tr.to,
      ...sideRoute,
      selected,
      style: selected
        ? {
            stroke,
            strokeWidth: 3,
            ...(tr.requiresApproval ? { strokeDasharray: '8 4' } : {}),
          }
        : tr.requiresApproval
          ? { stroke: color, strokeWidth: 2, strokeDasharray: '8 4' }
          : { stroke: color, strokeWidth: 1.5 },
      // 方向箭头：颜色与边一致（直接推进灰、需审核红、选中高亮主色）
      markerEnd: { type: MarkerType.ArrowClosed, color: stroke, width: 18, height: 18 },
      data: tr,
      selectable: true,
    };
    if (planEntry) {
      // 正交走廊边：应用拖动微调覆盖后生成自定义 path（经 edge-ortho 插槽渲染）
      const o = {
        ...DEFAULT_EDGE_OVERRIDES[id],
        ...savedLayout.value?.edgeOverrides?.[id],
        ...edgeOverrides.get(id),
      };
      const path = orthoPath({
        ...planEntry,
        laneY: o?.laneY ?? planEntry.laneY,
        sx: o?.sx ?? planEntry.sx,
        tx: o?.tx ?? planEntry.tx,
      });
      ORTHO_PATHS.set(id, path);
      return { ...base, type: 'ortho' } as Edge;
    }
    ORTHO_PATHS.delete(id);
    return { ...base, type: 'smoothstep', pathOptions: { offset, borderRadius: 6 } } as Edge;
  });
}

const edges = ref<Edge[]>([]);

// 定义加载或选中边变化时重建边（保留节点拖动位置：节点数组独立维护）
watch([definition, selectedEdgeId], () => {
  edges.value = buildEdges();
});

/** Vue Flow 选择机制回传（点击选中/点空白取消等）。
 *  注意：Vue Flow 单选模式会交叉取消（点节点连带 deselect 边、点边连带 deselect 节点），
 *  而本页要求边/节点选中相互独立，因此仅以 select:true 变更更新本组件模型，
 *  并立即重建边数组抵消 store 的交叉取消；取消统一走 pane-click / Esc / 抽屉关闭。 */
function onEdgesChange(changes: EdgeChange[]) {
  const selectChanges = changes.filter((c) => c.type === 'select');
  if (!selectChanges.length) return;
  const selChange = [...selectChanges].reverse().find((c) => c.selected);
  if (selChange) selectedEdgeId.value = selChange.id;
  edges.value = buildEdges();
}

/** 节点选中：只读 select:true 更新模型；不重建节点数组（保留拖动后的位置） */
// eslint-disable-next-line @typescript-eslint/no-explicit-any -- NodeChange 泛型在本文件规模下触发 TS2589，按结构取值
function onNodesChange(changes: any[]) {
  // 拖动位置变化：同步到 nodes 引用并重建边——正交路径基于实时位置生成，
  // 拖动后箭头与矩形保持连接
  const positionChanges = changes.filter((c) => c?.type === 'position' && c.position);
  if (positionChanges.length) {
    for (const c of positionChanges) {
      // @ts-expect-error -- Node 泛型联合在本文件规模下触发 TS2589 实例化深度误报；运行时行为有测试覆盖
      const node = nodes.value.find((nd) => nd.id === c.id);
      if (node) node.position = { x: c.position.x, y: c.position.y };
    }
    edges.value = buildEdges();
  }
  const selectChanges = changes.filter((c) => c?.type === 'select');
  if (!selectChanges.length) return;
  const selChange = [...selectChanges].reverse().find((c) => c.selected);
  if (selChange) selectedNodeId.value = selChange.id;
}

function clearSelection() {
  selectedEdgeId.value = null;
  selectedNodeId.value = null;
}

/** 选中边的两端状态节点集合（注入自定义节点实现联动高亮） */
const highlightedStatuses = computed<Set<string>>(() => {
  if (!selectedEdgeId.value) return new Set<string>();
  const tr = (definition.value?.transitions ?? []).find(
    (t) => t.enabled && `${t.from}->${t.to}` === selectedEdgeId.value,
  );
  return tr ? new Set([tr.from, tr.to]) : new Set<string>();
});
provide('rp-highlightedStatuses', highlightedStatuses);
provide('rp-selectedNode', selectedNodeId);

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') clearSelection();
}

// ── 交互 ────────────────────────────────────────────────────────────────

/** 单击边：仅选中高亮，不打开抽屉 */
function onEdgeClick({ edge }: { edge: Edge }) {
  selectedEdgeId.value = edge.id;
}

/** 双击边：选中并打开迁移详情抽屉 */
function onEdgeDoubleClick({ edge }: { edge: Edge }) {
  selectedEdgeId.value = edge.id;
  selectedRule.value = (edge.data as RegistrationProcessTransition) ?? null;
  selectedStatus.value = null;
  drawerMode.value = 'edge';
  drawerVisible.value = true;
}

/** 单击节点：仅选中高亮，不打开抽屉 */
function onNodeClick({ node }: { node: Node }) {
  selectedNodeId.value = node.id;
}

/** 双击节点：选中并打开该状态的释义详情抽屉（与边详情同一抽屉） */
function onNodeDoubleClick({ node }: { node: Node }) {
  selectedNodeId.value = node.id;
  clearHoverTimer();
  hideTooltip();
  selectedStatus.value = node.id;
  selectedRule.value = null;
  drawerMode.value = 'status';
  drawerVisible.value = true;
}

/** 重置布局：恢复默认坐标并重建视图（拖动仅本地生效，不持久化） */
function resetLayout() {
  edgeOverrides.clear();
  nodes.value = buildNodes();
  edges.value = buildEdges();
  layoutVersion.value++;
}

/** 保存布局（仅 SUPER_ADMIN 可见入口）：全量快照当前节点坐标与全部正交连线折线段 */
async function saveLayout() {
  const nodesOut: NonNullable<RegistrationProcessLayout['nodes']> = {};
  for (const n of nodes.value) {
    nodesOut[n.id] = { x: Math.round(n.position.x), y: Math.round(n.position.y) };
  }
  const edgeOverridesOut: NonNullable<RegistrationProcessLayout['edgeOverrides']> = {};
  for (const [id, path] of ORTHO_PATHS) {
    const nums = path.match(/-?\d+\.?\d*/g)?.map(Number) ?? [];
    if (nums.length >= 5) {
      edgeOverridesOut[id] = { sx: Math.round(nums[0]), laneY: Math.round(nums[3]), tx: Math.round(nums[4]) };
    }
  }
  const snapshot: RegistrationProcessLayout = { nodes: nodesOut, edgeOverrides: edgeOverridesOut };
  try {
    await registrationProcessLayoutApi.save(snapshot);
    savedLayout.value = snapshot;
    ElMessage.success(t('registrationProcess.saveLayoutSuccess'));
  } catch {
    ElMessage.error(t('registrationProcess.saveLayoutFail'));
  }
}

function quorumLabel(rule: RegistrationProcessTransition): string {
  return rule.quorumType === 'AT_LEAST'
    ? t('registrationProcess.quorumAtLeast', { count: rule.quorumCount ?? 1 })
    : t('registrationProcess.quorumAnyOne');
}

onMounted(async () => {
  loading.value = true;
  try {
    try {
      savedLayout.value = sanitizeLayout(await registrationProcessLayoutApi.get());
    } catch {
      savedLayout.value = null; // 无保存记录或读取失败：回落内置默认
    }
    definition.value = await registrationProcessApi.get();
    nodes.value = buildNodes();
  } catch {
    loadError.value = true;
    ElMessage.error(t('registrationProcess.loadError'));
  } finally {
    loading.value = false;
  }
  window.addEventListener('keydown', onKeydown);
  window.addEventListener('mousemove', onWindowMouseMove);
});

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown);
  window.removeEventListener('mousemove', onWindowMouseMove);
  clearHoverTimer();
});
</script>

<style scoped lang="scss">
.registration-process-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.header-section {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 16px;
  flex-shrink: 0;

  h2 {
    margin: 0 0 4px;
  }

  .subtitle {
    margin: 0;
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }
}

.header-tools {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
}

.recorded-note {
  margin-top: 16px;
  margin-bottom: 0;
  flex-shrink: 0;
}

.legend-card {
  margin-bottom: 16px;
  flex-shrink: 0;

  :deep(.el-card__body) {
    padding: 12px 16px;
  }
}

.legend {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 20px;

  &__title {
    font-weight: 600;
    font-size: 13px;
    margin-right: 4px;
  }

  &__item {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 12px;
    color: var(--el-text-color-regular);
  }

  &__node {
    width: 18px;
    height: 12px;
    border-radius: 3px;
    border: 1.5px solid var(--el-color-primary);
    background: var(--el-color-primary-light-9);

    &--documentation {
      border-color: var(--el-color-info);
      background: var(--el-color-info-light-9);
    }
  }

  &__edge {
    width: 26px;
    border-top: 2px solid #909399;

    &--approval {
      border-top-color: #f56c6c;
      border-top-style: dashed;
    }
  }
}

.graph-card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;

  :deep(.el-card__body) {
    padding: 0;
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
  }
}

.ortho-seg {
  fill: none;
  stroke: transparent;
  stroke-width: 12;
  pointer-events: stroke;

  &--v {
    cursor: ew-resize;
  }

  &--h {
    cursor: ns-resize;
  }
}

.process-flow {
  flex: 1;
  min-height: 0;
  background:
    radial-gradient(circle, var(--el-border-color-lighter) 1px, transparent 1px) 0 0 / 20px 20px;
}

.rule-detail {
  &__basis {
    margin-top: 16px;
  }
}

.status-detail {
  &__head {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;
  }

  &__code {
    font-family: monospace;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  &__section {
    font-size: 12px;
    font-weight: 600;
    color: var(--el-text-color-secondary);
    margin: 14px 0 4px;
  }

  &__text {
    margin: 0;
    font-size: 13px;
    line-height: 1.7;
    color: var(--el-text-color-regular);
  }
}
</style>

<style lang="scss">
/* 选中边提升层级，压过其他边（Vue Flow 选中类兜底） */
.vue-flow__edge.selected {
  z-index: 1002;
}

/* teleport 到 body，不能用 scoped：fixed 定位浮层，跟随视口，不被画布裁剪 */
.status-tooltip {  display: none;
  position: fixed;
  transform: translateX(-50%);
  width: 360px;
  max-height: 420px;
  overflow-y: auto;
  padding: 12px 14px;
  border-radius: 8px;
  border: 1px solid var(--el-border-color);
  background: var(--el-bg-color);
  box-shadow: var(--el-box-shadow-light);
  text-align: left;
  z-index: 3000;

  &--visible {
    display: block;
  }

  &--above {
    transform: translate(-50%, -100%);
  }

  &__title {
    font-weight: 600;
    font-size: 13px;
    margin-bottom: 4px;
    color: var(--el-text-color-primary);
  }

  &__category {
    font-size: 12px;
    color: var(--el-color-primary);
    margin-bottom: 8px;
  }

  &__section {
    font-size: 11px;
    font-weight: 600;
    color: var(--el-text-color-secondary);
    margin: 8px 0 2px;
  }

  &__text {
    margin: 0;
    font-size: 12px;
    line-height: 1.6;
    color: var(--el-text-color-regular);
  }
}
</style>
