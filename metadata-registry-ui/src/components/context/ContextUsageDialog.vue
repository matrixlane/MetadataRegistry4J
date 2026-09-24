@ -0,0 +1,166 @@
<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="780px"
    :close-on-click-modal="false"
    destroy-on-close
    @closed="reset"
  >
    <el-skeleton v-if="loading" :rows="6" animated />
    <div v-else-if="!response || response.groups.length === 0">
      <el-empty description="暂无引用" />
    </div>
    <div v-else class="usage-groups">
      <el-alert
        v-if="response.truncated"
        type="warning"
        :closable="false"
        style="margin-bottom: 12px;"
      >
        引用数量超过 100 条，仅显示前 100 条，请使用更精确的筛选条件。
      </el-alert>

      <div
        v-for="group in response.groups"
        :key="group.itemType"
        class="usage-group"
      >
        <h4 class="group-title">{{ group.itemTypeLabel }} ({{ group.items.length }})</h4>
        <el-table :data="flattenGroup(group)" size="small" style="width: 100%;">
          <el-table-column label="条目" min-width="160">
            <template #default="{ row }">
              <el-link type="primary" @click="navigateToItem(row.itemId)">
                {{ row.itemLabel || labelFor(row.itemId) }}
              </el-link>
            </template>
          </el-table-column>
          <el-table-column label="关联类型" width="100">
            <template #default="{ row }">
              {{ associationTypeLabel(row.associationType) }}
            </template>
          </el-table-column>
          <el-table-column label="指称" min-width="140">
            <template #default="{ row }">
              <span v-if="row.associationType === 'DEFINITION'">-</span>
              <span v-else>{{ row.designationValue || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="定义" min-width="140">
            <template #default="{ row }">
              <span v-if="row.associationType === 'DESIGNATION'">-</span>
              <span v-else>{{ row.definitionValue || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="可接受性" width="100">
            <template #default="{ row }">
              <el-tag
                v-if="row.acceptability"
                size="small"
                :type="acceptabilityType(row.acceptability)"
              >
                {{ $t('acceptability.' + row.acceptability) }}
              </el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { useI18n } from 'vue-i18n';
const { t } = useI18n();
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import type { ElTagType } from '@/types/common';
import { contextApi } from '@/api/resources';
import { useItemLabelCache } from '@/composables/useItemLabelCache';
import type { ContextUsageResponse, ContextUsageGroup, ContextUsageAssociation } from '@/types/common';

const { requestAll: requestLabels, labelFor } = useItemLabelCache();

interface Props {
  contextLabel?: string;
}

const props = defineProps<Props>();
const visible = ref(false);
const loading = ref(false);
const response = ref<ContextUsageResponse | null>(null);
const contextId = ref<string>('');
const router = useRouter();

const title = computed(() => {
  const label = props.contextLabel || t('context.title') || '上下文';
  return `「${label}」的引用`;
});

interface FlatRow extends ContextUsageAssociation {
  itemId: string;
  itemLabel?: string;
}

function flattenGroup(group: ContextUsageGroup): FlatRow[] {
  const rows: FlatRow[] = [];
  for (const item of group.items) {
    for (const assoc of item.associations) {
      rows.push({ ...assoc, itemId: item.itemId, itemLabel: item.itemLabel });
    }
  }
  return rows;
}

function associationTypeLabel(type: string): string {
  switch (type) {
    case 'DESIGNATION': return '指称';
    case 'DEFINITION': return '定义';
    case 'PAIRING': return '配对';
    default: return type;
  }
}

function acceptabilityType(acceptability: string): ElTagType {
  switch (acceptability) {
    case 'PREFERRED': return 'success';
    case 'ADMITTED': return 'primary';
    case 'DEPRECATED': return 'warning';
    case 'SUPERSEDED': return 'info';
    case 'OBSOLETE': return 'danger';
    default: return 'info';
  }
}

function navigateToItem(itemId: string) {
  router.push({ name: 'MetadataDetail', params: { id: itemId } });
  visible.value = false;
}

async function open(id: string, label?: string) {
  contextId.value = id;
  response.value = null;
  visible.value = true;
  loading.value = true;
  try {
    response.value = await contextApi.usages(id);
    if (response.value) {
      requestLabels(response.value.groups.flatMap((g) => g.items.map((it) => it.itemId)));
    }
  } catch (err: any) {
    ElMessage.error(err?.message || '加载引用失败');
  } finally {
    loading.value = false;
  }
}

function reset() {
  response.value = null;
  contextId.value = '';
}

defineExpose({ open });
</script>

<style scoped>
.usage-groups {
  max-height: 60vh;
  overflow-y: auto;
}
.usage-group {
  margin-bottom: 16px;
}
.usage-group:last-child {
  margin-bottom: 0;
}
.group-title {
  margin: 0 0 8px 0;
  font-size: 15px;
  font-weight: 600;
  color: #374151;
}
</style>
