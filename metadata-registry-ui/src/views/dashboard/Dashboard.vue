<template>
  <div>
    <div class="welcome-section">
      <h2 style="margin: 0; font-size: 24px; font-weight: 700; color: #1f2937;">概览</h2>
      <p style="margin: 8px 0 0; color: #6b7280; font-size: 14px;">
        欢迎回来，{{ auth.user?.displayName || auth.user?.username || '用户' }} — 以下是元数据注册中心的实时数据
      </p>
    </div>

    <el-row :gutter="16">
      <el-col :span="6" v-for="item in stats" :key="item.label">
        <el-card shadow="never" class="stat-card--dark">
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
      </el-col>
    </el-row>

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
          <el-timeline class="activity-timeline">
            <el-timeline-item
              v-for="(activity, index) in activities"
              :key="index"
              :type="activity.type"
              :timestamp="activity.timestamp"
            >
              <div class="timeline-content">{{ activity.content }}</div>
            </el-timeline-item>
          </el-timeline>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';
import * as echarts from 'echarts';
import { useAuthStore } from '@/stores/auth';
import { PieChart, Clock } from '@element-plus/icons-vue';

const auth = useAuthStore();

const stats = ref([
  { label: '数据元', value: 4, unit: '项', sub: 'Data Element', badge: '正常', trend: '+1 本周', trendDown: false },
  { label: '对象类', value: 3, unit: '项', sub: 'Object Class', badge: '正常', trend: '+0 本周', trendDown: false },
  { label: '特性', value: 6, unit: '项', sub: 'Property', badge: '正常', trend: '+2 本周', trendDown: false },
  { label: '注册项', value: 16, unit: '项', sub: 'Administered Item', badge: '关注', trend: '+4 本周', trendDown: false },
]);

const activities = ref([
  { content: '管理员创建数据元 DE-ORDER-STATUS', timestamp: '2026-05-08 10:30', type: 'primary' as const },
  { content: '注册机构 示例注册机构 完成初始化', timestamp: '2026-05-08 10:00', type: 'success' as const },
  { content: '系统初始化完成', timestamp: '2026-05-08 09:55', type: 'info' as const },
]);

// 注册状态分布甜甜圈图：同色系多阶绿（参照 EMS demo 单色相递进）
const donutRef = ref<HTMLDivElement>();
let donutChart: echarts.ECharts | null = null;

const statusDistribution = [
  { name: '首选', value: 8 },
  { name: '合格', value: 4 },
  { name: '已记录', value: 3 },
  { name: '草稿', value: 1 },
];

function buildDonutOption(): echarts.EChartsOption {
  const total = statusDistribution.reduce((sum, item) => sum + item.value, 0);
  return {
    color: ['#16a34a', '#4ade80', '#2d5a3d', '#86efac'],
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
        data: statusDistribution,
      },
    ],
  };
}

onMounted(() => {
  if (donutRef.value) {
    donutChart = echarts.init(donutRef.value);
    donutChart.setOption(buildDonutOption());
  }
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
