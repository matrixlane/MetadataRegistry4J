<template>
  <div class="mapping-network-view">
    <!-- Header -->
    <div class="header-section">
      <div class="header-left">
        <h2>{{ $t('nav.mappingNetwork') }}</h2>
        <p class="subtitle">{{ $t('mappingNetwork.subtitle') }}</p>
      </div>
    </div>

    <!-- Controls -->
    <el-card shadow="never" class="controls-card">
      <el-form :inline="true" size="small" label-width="auto">
        <el-form-item :label="$t('mappingNetwork.centerItem')">
          <div style="width: 320px">
            <SmartItemPicker
              v-model="centerItemId"
              :scope="'all'"
              clearable
              :placeholder="$t('mappingNetwork.centerPlaceholder')"
            />
          </div>
        </el-form-item>
        <el-form-item :label="$t('mappingNetwork.depth')">
          <el-select v-model="depth" style="width: 100px">
            <el-option :value="1" label="1" />
            <el-option :value="2" label="2" />
            <el-option :value="3" label="3" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('mappingNetwork.filterTypes')">
          <el-select v-model="filterTypes" multiple clearable collapse-tags style="width: 240px">
            <el-option value="same as" :label="$t('itemMapping.degree.SAME_AS')" />
            <el-option value="semantically equivalent" :label="$t('itemMapping.degree.SEMANTICALLY_EQUIVALENT')" />
            <el-option value="derived from" :label="$t('itemMapping.degree.DERIVED_FROM')" />
            <el-option value="has derivation" :label="$t('itemMapping.degree.HAS_DERIVATION')" />
            <el-option value="broader than" :label="$t('itemMapping.degree.BROADER_THAN')" />
            <el-option value="narrower than" :label="$t('itemMapping.degree.NARROWER_THAN')" />
            <el-option value="contains" :label="$t('itemMapping.degree.CONTAINS')" />
            <el-option value="contained in" :label="$t('itemMapping.degree.CONTAINED_IN')" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="loadNetwork">
            <el-icon><Search /></el-icon>
            {{ $t('mappingNetwork.query') }}
          </el-button>
          <el-button @click="resetView">{{ $t('mappingNetwork.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Truncation warning -->
    <el-alert
      v-if="truncated"
      :title="$t('mappingNetwork.truncatedWarning')"
      type="warning"
      show-icon
      :description="$t('mappingNetwork.truncatedDesc', { count: totalCount })"
      style="margin-bottom: 16px"
      closable
    />

    <!-- Graph -->
    <el-card shadow="never" class="graph-card">
      <div class="graph-info" v-if="nodes.length > 0">
        <span class="info-badge">
          <el-icon><Connection /></el-icon>
          {{ $t('mappingNetwork.nodes', { count: nodes.length }) }}
        </span>
        <span class="info-badge">
          <el-icon><Link /></el-icon>
          {{ $t('mappingNetwork.edges', { count: edges.length }) }}
        </span>
      </div>
      <div ref="chartRef" class="graph-container"></div>
      <div v-if="nodes.length === 0 && !loading" class="empty-state">
        <el-empty :description="$t('mappingNetwork.empty')" />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue';
import { useRouter } from 'vue-router';
import * as echarts from 'echarts';
import { Search, Connection, Link } from '@element-plus/icons-vue';
import { getMappingNetwork } from '@/api/resources';
import { useItemLabelCache } from '@/composables/useItemLabelCache';
import SmartItemPicker from '@/components/SmartItemPicker.vue';
import { ElMessage } from 'element-plus';

interface NetworkNode {
  itemId: string;
  depth: number;
  isCenter: boolean;
}

interface NetworkEdge {
  mappingId: string;
  sourceId: string;
  targetId: string;
  degree: string;
  description: string | null;
}

interface NetworkResponse {
  nodes: NetworkNode[];
  edges: NetworkEdge[];
  truncated: boolean;
  totalCount: number;
}

const router = useRouter();
const { requestAll: requestLabels, labelFor, version: labelVersion } = useItemLabelCache();

// 标签异步解析完成后重绘图（节点显示「标识符 | 指称」而非 id）
watch(labelVersion, () => {
  if (nodes.value.length > 0) renderChart();
});

// State
const centerItemId = ref('');
const depth = ref(3);
const filterTypes = ref<string[]>([]);
const loading = ref(false);
const nodes = ref<NetworkNode[]>([]);
const edges = ref<NetworkEdge[]>([]);
const truncated = ref(false);
const totalCount = ref(0);

const chartRef = ref<HTMLDivElement>();
let chartInstance: echarts.ECharts | null = null;

// Color mapping for mapping degrees
const typeColors: Record<string, string> = {
  'same as': '#67c23a',
  'semantically equivalent': '#8bc34a',
  'derived from': '#409eff',
  'has derivation': '#64b5f6',
  'broader than': '#e6a23c',
  'narrower than': '#fb8c00',
  'contains': '#ab47bc',
  'contained in': '#8e24aa',
  'related to': '#909399',
  'semantically similar': '#a5d6a7',
  'child of': '#26a69a',
  'parent of': '#00897b',
  'instance of': '#5c6bc0',
  'has instance': '#3949ab',
};

const typeLabels: Record<string, string> = {
  'same as': '同一',
  'semantically equivalent': '语义等价',
  'derived from': '派生自',
  'has derivation': '有派生',
  'broader than': '宽于',
  'narrower than': '窄于',
  'contains': '包含',
  'contained in': '被包含',
  'related to': '相关',
  'semantically similar': '语义相似',
  'child of': '子级',
  'parent of': '父级',
  'instance of': '实例',
  'has instance': '有实例',
};

// Depth-based node sizes
function getNodeSize(depth: number, isCenter: boolean): number {
  if (isCenter) return 50;
  switch (depth) {
    case 0: return 50;
    case 1: return 36;
    case 2: return 28;
    case 3: return 22;
    default: return 20;
  }
}

// Depth-based node opacity
function getNodeOpacity(depth: number): number {
  return Math.max(0.4, 1 - depth * 0.2);
}

// Build ECharts graph options
function buildGraphOption(): echarts.EChartsOption {
  const categories = [
    { name: 'center', itemStyle: { color: '#409eff', borderColor: '#2563eb' } },
    { name: 'layer1', itemStyle: { color: '#67c23a', borderColor: '#529b2e' } },
    { name: 'layer2', itemStyle: { color: '#e6a23c', borderColor: '#b88230' } },
    { name: 'layer3', itemStyle: { color: '#909399', borderColor: '#6b6f75' } },
  ];

  const graphNodes = nodes.value.map((n) => {
    const depthIndex = n.isCenter ? 0 : Math.min(n.depth, 3);
    return {
      id: n.itemId,
      // 节点标签经共享缓存反查「标识符 | 指称」，绝不显示 id/短码
      name: labelFor(n.itemId),
      value: n.itemId,
      category: depthIndex,
      symbolSize: getNodeSize(n.depth, n.isCenter),
      itemStyle: {
        opacity: getNodeOpacity(n.depth),
      },
      label: {
        show: n.isCenter || n.depth <= 2,
        fontSize: n.isCenter ? 12 : 10,
        fontWeight: (n.isCenter ? 'bold' : 'normal') as 'bold' | 'normal',
      },
      // Store full data for tooltip
      rawDepth: n.depth,
      rawIsCenter: n.isCenter,
    };
  });

  const graphEdges = edges.value.map((e) => ({
    id: e.mappingId,
    source: e.sourceId,
    target: e.targetId,
    value: (e.degree === 'same as' || e.degree === 'semantically equivalent') ? 2 : 1,
    lineStyle: {
      color: typeColors[e.degree] || '#999',
      width: (e.degree === 'same as' || e.degree === 'semantically equivalent') ? 2.5 : 2,
      curveness: 0.15,
      type: ((e.degree === 'same as' || e.degree === 'semantically equivalent') ? 'solid' : 'dashed') as 'solid' | 'dashed',
    },
    // Store description for tooltip
    rawDegree: e.degree,
    rawDescription: e.description,
  }));

  return {
    title: {
      show: false,
    },
    tooltip: {
      trigger: 'item' as const,
      formatter: (params: any) => {
        if (params.dataType === 'node') {
          const node = params.data as any;
          const depthLabel = node.rawIsCenter ? '中心节点' : `第 ${node.rawDepth} 层`;
          return `<strong>元数据项</strong><br/>
                  ${node.name}<br/>
                  ${depthLabel}`;
        } else if (params.dataType === 'edge') {
          const edge = params.data as any;
          const typeLabel = typeLabels[edge.rawDegree] || edge.rawDegree;
          let html = `<strong>映射关系</strong><br/>
                      类型: ${typeLabel} (${edge.rawDegree})`;
          if (edge.rawDescription) {
            html += `<br/>描述: ${edge.rawDescription}`;
          }
          return html;
        }
        return '';
      },
    },
    series: [
      {
        type: 'graph',
        layout: 'force',
        force: {
          repulsion: 300,
          edgeLength: [80, 200],
          gravity: 0.2,
          friction: 0.1,
        },
        roam: true,
        draggable: true,
        data: graphNodes,
        edges: graphEdges,
        categories,
        focusNodeAdjacency: true,
        edgeSymbol: ['none', 'arrow'],
        edgeSymbolSize: [0, 8],
        label: {
          show: true,
          position: 'bottom',
          color: '#333',
        },
        lineStyle: {
          color: 'source',
          opacity: 0.7,
        },
        emphasis: {
          focus: 'adjacency' as const,
          lineStyle: {
            width: 4,
          },
        },
      },
    ],
  };
}

// Render the chart
function renderChart() {
  if (!chartRef.value) return;

  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value);
  }

  const option = buildGraphOption();
  chartInstance.setOption(option, true);

  // Bind click event for node navigation
  chartInstance.off('click');
  chartInstance.on('click', (params: any) => {
    if (params.dataType === 'node') {
      const nodeId = params.data.value;
      router.push(`/data-dictionary/${nodeId}`);
    }
  });

  // Resize handler
  chartInstance.resize();
}

// Load network data from API
async function loadNetwork() {
  if (!centerItemId.value?.trim()) {
    ElMessage.warning('请输入中心元数据项 ID');
    return;
  }

  loading.value = true;
  try {
    const response = await getMappingNetwork(
      centerItemId.value.trim(),
      depth.value,
      filterTypes.value.length > 0 ? filterTypes.value : undefined
    ) as NetworkResponse;

    nodes.value = response.nodes;
    edges.value = response.edges;
    truncated.value = response.truncated;
    totalCount.value = response.totalCount;
    requestLabels(response.nodes.map((n) => n.itemId));

    await nextTick();
    renderChart();
  } catch (err: any) {
    ElMessage.error(err?.message || '查询映射网络失败');
    nodes.value = [];
    edges.value = [];
  } finally {
    loading.value = false;
  }
}

// Reset view
function resetView() {
  centerItemId.value = '';
  depth.value = 3;
  filterTypes.value = [];
  nodes.value = [];
  edges.value = [];
  truncated.value = false;
  totalCount.value = 0;
  if (chartInstance) {
    chartInstance.clear();
  }
}

// Watch for window resize
function handleResize() {
  chartInstance?.resize();
}

// Lifecycle
onMounted(() => {
  window.addEventListener('resize', handleResize);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize);
  chartInstance?.dispose();
  chartInstance = null;
});

// Expose for programmatic usage
defineExpose({
  loadNetwork,
  resetView,
});
</script>

<style scoped lang="scss">
.mapping-network-view {
  padding: 20px;

  .header-section {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;

    h2 {
      margin: 0;
      font-size: 22px;
      font-weight: 600;
      color: #1f2937;
    }

    .subtitle {
      margin: 4px 0 0;
      color: #6b7280;
      font-size: 13px;
    }
  }

  .controls-card {
    margin-bottom: 16px;

    :deep(.el-card__body) {
      padding: 16px 20px;
    }
  }

  .graph-card {
    position: relative;

    .graph-info {
      position: absolute;
      top: 12px;
      right: 16px;
      z-index: 10;
      display: flex;
      gap: 16px;

      .info-badge {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        font-size: 12px;
        color: #6b7280;
        background: #f3f4f6;
        padding: 4px 10px;
        border-radius: 12px;
      }
    }

    .graph-container {
      width: 100%;
      height: 600px;
      min-height: 400px;
    }

    .empty-state {
      display: flex;
      justify-content: center;
      align-items: center;
      height: 400px;
    }
  }
}
</style>
