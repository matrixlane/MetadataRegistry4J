<template>
  <div>
    <div class="welcome-section">
      <h2 style="margin: 0; font-size: 24px; font-weight: 700; color: #1f2937;">概览</h2>
      <p style="margin: 8px 0 0; color: #6b7280; font-size: 14px;">
        欢迎回来，{{ auth.user?.displayName || auth.user?.username || '用户' }} — 以下是元数据注册中心的实时数据
      </p>
    </div>

    <el-row :gutter="16">
      <el-col :span="6" v-for="(item, index) in stats" :key="item.label">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon">
            <el-icon :size="28" :color="iconColors[index % iconColors.length]">
              <component :is="item.icon" />
            </el-icon>
          </div>
          <div class="stat-value">{{ item.value }}</div>
          <div class="stat-label">{{ item.label }}</div>
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
          <div class="chart-placeholder">
            <div class="chart-visual">
              <svg viewBox="0 0 200 200" width="160" height="160">
                <circle cx="100" cy="100" r="80" fill="none" stroke="#e5e7eb" stroke-width="20" />
                <circle cx="100" cy="100" r="80" fill="none" stroke="#2563eb" stroke-width="20"
                  stroke-dasharray="376.99" stroke-dashoffset="94.25" stroke-linecap="round"
                  transform="rotate(-90 100 100)" />
                <circle cx="100" cy="100" r="80" fill="none" stroke="#3b82f6" stroke-width="20"
                  stroke-dasharray="376.99" stroke-dashoffset="282.74" stroke-linecap="round"
                  transform="rotate(-90 100 100)" />
              </svg>
              <div class="chart-center">
                <div class="chart-number">4</div>
                <div class="chart-text">数据元</div>
              </div>
            </div>
            <div class="chart-legend">
              <div class="legend-item">
                <span class="legend-dot" style="background: #2563eb;"></span>
                <span>已注册 (3)</span>
              </div>
              <div class="legend-item">
                <span class="legend-dot" style="background: #3b82f6;"></span>
                <span>草稿 (1)</span>
              </div>
            </div>
          </div>
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
import { ref } from 'vue';
import { useAuthStore } from '@/stores/auth';
import {
  DataLine,
  Grid,
  SetUp,
  Collection,
  PieChart,
  Clock,
} from '@element-plus/icons-vue';

const auth = useAuthStore();

const iconColors = ['#2563eb', '#3b82f6', '#60a5fa', '#1e40af'];

const stats = ref([
  { label: '数据元', value: 4, icon: DataLine },
  { label: '对象类', value: 3, icon: Grid },
  { label: '特性', value: 6, icon: SetUp },
  { label: '注册项', value: 16, icon: Collection },
]);

const activities = ref([
  { content: '管理员创建数据元 DE-ORDER-STATUS', timestamp: '2026-05-08 10:30', type: 'primary' as const },
  { content: '注册机构 示例注册机构 完成初始化', timestamp: '2026-05-08 10:00', type: 'success' as const },
  { content: '系统初始化完成', timestamp: '2026-05-08 09:55', type: 'info' as const },
]);
</script>

<style scoped lang="scss">
.welcome-section {
  margin-bottom: 24px;
}

.stat-card {
  text-align: center;
  border-radius: 12px;
  background: #ffffff;
  border: 1px solid #f1f5f9;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04), 0 4px 16px rgba(0, 0, 0, 0.03);
  transition: all 0.3s ease;
  overflow: hidden;
  position: relative;
  padding: 24px 16px;

  &:hover {
    transform: translateY(-3px);
    box-shadow: 0 12px 28px rgba(37, 99, 235, 0.1);
  }

  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    height: 3px;
    background: linear-gradient(90deg, #2563eb, #3b82f6);
  }

  .stat-icon {
    margin-bottom: 12px;
    display: flex;
    justify-content: center;
  }

  .stat-value {
    font-size: 32px;
    font-weight: 800;
    color: #1e40af;
    line-height: 1.2;
    letter-spacing: -0.5px;
  }

  .stat-label {
    margin-top: 8px;
    color: #6b7280;
    font-size: 14px;
    font-weight: 500;
  }
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

.chart-placeholder {
  min-height: 220px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 32px;

  .chart-visual {
    position: relative;
    display: flex;
    align-items: center;
    justify-content: center;

    .chart-center {
      position: absolute;
      text-align: center;

      .chart-number {
        font-size: 28px;
        font-weight: 800;
        color: #1e40af;
        line-height: 1;
      }

      .chart-text {
        font-size: 12px;
        color: #6b7280;
        margin-top: 4px;
      }
    }
  }

  .chart-legend {
    display: flex;
    flex-direction: column;
    gap: 12px;

    .legend-item {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 13px;
      color: #4b5563;

      .legend-dot {
        width: 10px;
        height: 10px;
        border-radius: 3px;
      }
    }
  }
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
