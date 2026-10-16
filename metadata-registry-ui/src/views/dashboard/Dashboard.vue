<template>
  <div>
    <div class="welcome-section">
      <h2 style="margin: 0; font-size: 24px; font-weight: 700; color: #1f2937;">概览</h2>
      <p style="margin: 8px 0 0; color: #6b7280; font-size: 14px;">
        欢迎回来，{{ auth.user?.displayName || auth.user?.username || '用户' }} — 以下是元数据注册中心的实时数据
      </p>
    </div>

    <div class="stat-row">
      <el-card v-for="item in stats" :key="item.label" shadow="never" class="stat-card--dark stat-row__item">
        <div class="stat-card__label">{{ item.label }}</div>
        <span class="stat-card__badge">{{ item.badge }}</span>
        <div class="stat-card__value">
          {{ item.value }}<span class="stat-card__unit">{{ item.unit }}</span>
        </div>
        <div class="stat-card__sub">{{ item.sub }}</div>
        <span
          class="stat-card__trend"
          :class="item.trendDown ? 'stat-card__trend--down' : 'stat-card__trend--up'"
        >{{ item.trend }}</span>
      </el-card>
    </div>

    <el-row :gutter="16" style="margin-top: 20px;">
      <el-col :span="12">
        <el-card shadow="hover" class="dashboard-card">
          <template #header>
            <div class="card-header">
              <el-icon><PieChart /></el-icon>
              <span>数据元注册状态分布</span>
            </div>
          </template>
          <div ref="donutRef" class="donut-chart"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover" class="dashboard-card">
          <template #header>
            <div class="card-header">
              <el-icon><Clock /></el-icon>
              <span>近期注册项变更</span>
            </div>
          </template>
          <el-timeline v-if="activities.length" class="activity-timeline">
            <el-timeline-item
              v-for="(activity, index) in activities"
              :key="index"
              :type="activity.type"
              :timestamp="activity.timestamp"
            >
              <div class="timeline-content">{{ activity.content }}</div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无注册项变更记录" :image-size="80" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';
import * as echarts from 'echarts';
import { ElMessage } from 'element-plus';
import { useI18n } from 'vue-i18n';
import { useAuthStore } from '@/stores/auth';
import { dashboardApi } from '@/api/system';
import { PieChart, Clock } from '@element-plus/icons-vue';

const auth = useAuthStore();
const { t } = useI18n();

interface StatCard {
  label: string;
  value: number;
  unit: string;
  sub: string;
  badge: string;
  trend: string;
  trendDown: boolean;
}

const stats = ref<StatCard[]>([]);

interface ActivityItem {
  content: string;
  timestamp: string;
  type: 'primary' | 'success' | 'info' | 'warning' | 'danger';
}

const activities = ref<ActivityItem[]>([]);

// 注册状态分布甜甜圈图：同色系多阶绿（参照 EMS demo 单色相递进）
const donutRef = ref<HTMLDivElement>();
let donutChart: echarts.ECharts | null = null;

const statusDistribution = ref<{ name: string; value: number }[]>([]);
const DONUT_COLORS = ['#16a34a', '#4ade80', '#2d5a3d', '#86efac', '#15803d', '#22c55e', '#365314', '#14532d', '#052e16', '#6ee7b7'];

function buildDonutOption(): echarts.EChartsOption {
  const total = statusDistribution.value.reduce((sum, item) => sum + item.value, 0);
  return {
    color: DONUT_COLORS,
    title: {
      text: String(total),
      subtext: '注册项总数',
      left: 'center',
      top: '36%',
      textStyle: { fontSize: 28, fontWeight: 800, color: '#1e4530' },
      subtextStyle: { fontSize: 12, color: '#6b7280' },
    },
    tooltip: { trigger: 'item' },
    legend: {
      bottom: 0,
      icon: 'circle',
      itemWidth: 8,
      textStyle: { fontSize: 12, color: '#4b5563' },
    },
    series: [
      {
        type: 'pie',
        radius: ['55%', '78%'],
        center: ['50%', '44%'],
        label: { show: false },
        emphasis: { scaleSize: 4 },
        data: statusDistribution.value,
      },
    ],
  };
}

function formatDateTime(value: string): string {
  // 后端 LocalDateTime 序列化为 ISO 本地时间（无时区），截取到分钟
  return value ? value.slice(0, 16).replace('T', ' ') : '';
}

async function loadDashboard() {
  try {
    const d = await dashboardApi.getStats();
    const trendOf = (n: number) => `+${n} 本周`;
    stats.value = [
      { label: '受管项', value: d.administeredItems, unit: '项', sub: 'Administered Item', badge: '正常', trend: trendOf(d.administeredItemsCreatedThisWeek), trendDown: false },
      { label: '数据元', value: d.dataElements, unit: '项', sub: 'Data Element', badge: '正常', trend: trendOf(d.dataElementsCreatedThisWeek), trendDown: false },
      { label: '值域', value: d.valueDomains, unit: '项', sub: 'Value Domain', badge: '正常', trend: trendOf(d.valueDomainsCreatedThisWeek), trendDown: false },
      { label: '数据元概念', value: d.dataElementConcepts, unit: '项', sub: 'Data Element Concept', badge: '正常', trend: trendOf(d.dataElementConceptsCreatedThisWeek), trendDown: false },
      { label: '概念域', value: d.conceptualDomains, unit: '项', sub: 'Conceptual Domain', badge: '正常', trend: trendOf(d.conceptualDomainsCreatedThisWeek), trendDown: false },
    ];
    statusDistribution.value = d.registrationStatusDistribution.map((s) => ({
      name: s.status ? t(`registrationStatus.${s.status}`) : '无状态',
      value: s.count,
    }));
    activities.value = d.recentChanges.map((r) => ({
      content: r.changeDescription
        ? `${r.name ?? '未命名条目'}：${r.changeDescription}`
        : `更新 ${r.name ?? '未命名条目'}`,
      timestamp: formatDateTime(r.changedAt),
      type: 'primary',
    }));
    donutChart?.setOption(buildDonutOption());
  } catch {
    ElMessage.error('概览统计数据加载失败');
  }
}

onMounted(() => {
  if (donutRef.value) {
    donutChart = echarts.init(donutRef.value);
    donutChart.setOption(buildDonutOption());
  }
  loadDashboard();
});

onBeforeUnmount(() => {
  donutChart?.dispose();
  donutChart = null;
});
</script>

<style scoped lang="scss">
.welcome-section {
  margin-bottom: 24px;
}

// 五张统计卡片等宽排列（24 栅格不能被 5 整除，用 flex 取代 el-row/el-col）
.stat-row {
  display: flex;
  gap: 16px;

  &__item {
    flex: 1;
    min-width: 0;
  }
}

.donut-chart {
  width: 100%;
  height: 260px;
}

.dashboard-card {
  border-radius: 12px;
  border: 1px solid #f1f5f9;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04), 0 4px 16px rgba(0, 0, 0, 0.03);
  transition: box-shadow 0.3s ease;

  &:hover {
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.06);
  }

  :deep(.el-card__header) {
    padding: 16px 20px;
    border-bottom: 1px solid #f3f4f6;
  }

  :deep(.el-card__body) {
    padding: 20px;
  }
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 15px;
  color: #374151;
}

.activity-timeline {
  padding-left: 4px;

  .timeline-content {
    font-size: 14px;
    color: #374151;
    line-height: 1.5;
  }

  :deep(.el-timeline-item__timestamp) {
    color: #9ca3af;
    font-size: 12px;
  }
}
</style>
