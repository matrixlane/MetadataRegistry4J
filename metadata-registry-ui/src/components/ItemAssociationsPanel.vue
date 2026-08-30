<!--
  ItemAssociationsPanel —— 四实体标准关联的一对多管理面板。
  正向/反向仅是同一关联的存储方向，界面上不作区分（无「被引用」概念）。

  依据 ISO/IEC 11179-31:2023：
  - data_element_meaning（§7.2.3.3/§7.6.3.2）：DE 0..* → 0..1 DEC
  - data_element_domain（§7.2.3.2/§7.6.3.1）：DE 0..* → 0..1 VD
  - value_domain_meaning（§7.2.3.1/§7.4.4.1）：VD 0..* → 0..1 CD
  - data_element_concept_domain（§7.2.3.4）：DEC 0..* ↔ 0..* CD

  每个 section 展示焦点实体在某一关联类型下的一组对象，支持模糊搜索（可多选）批量添加与解除；
  写操作按关联类型的标准方向（source → target）调用 /api/v1/associations/*。
-->
<template>
  <div class="associations-panel">
    <el-alert
      v-if="!itemId"
      type="info"
      :closable="false"
      title="保存后即可管理关联。"
    />
    <template v-else>
      <el-card
        v-for="section in sections"
        :key="section.relationType + '-' + section.direction"
        shadow="never"
        class="assoc-section"
      >
        <template #header>
          <div class="assoc-section-header">
            <span>{{ section.title }}</span>
            <el-tag size="small" effect="plain">{{ entriesOf(section).length }}</el-tag>
          </div>
        </template>
        <el-table :data="entriesOf(section)" size="small" v-loading="loading">
          <el-table-column label="标识符" width="150">
            <template #default="{ row }">
              <span class="assoc-identifier">{{ row.identifier || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="版本" width="70">
            <template #default="{ row }">{{ row.version ? 'v' + row.version : '—' }}</template>
          </el-table-column>
          <el-table-column label="指称" min-width="140">
            <template #default="{ row }">{{ row.label || '—' }}</template>
          </el-table-column>
          <el-table-column label="创建时间" width="140">
            <template #default="{ row }">{{ fmt(row.creationDateTime) || '—' }}</template>
          </el-table-column>
          <el-table-column label="最后编辑" width="140">
            <template #default="{ row }">{{ fmt(row.lastChangeDateTime) || '—' }}</template>
          </el-table-column>
          <el-table-column width="60" align="center">
            <template #default="{ row }">
              <el-button
                type="danger"
                size="small"
                text
                circle
                :disabled="mutating"
                @click="onRemove(section, row)"
              >
                <el-icon><Delete /></el-icon>
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="assoc-add">
          <SmartItemPicker
            v-model="addValues[sectionKey(section)]"
            :item-types="section.targetItemTypes"
            :show-advanced-filter="false"
            :placeholder="section.addPlaceholder"
            :disabled="mutating"
            :allow-multiple="true"
            clearable
          />
          <el-button
            type="primary"
            size="small"
            :disabled="mutating || !selectionOf(section).length"
            @click="onAdd(section)"
          >
            添加
          </el-button>
        </div>
      </el-card>
    </template>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue';
import { Delete } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import dayjs from 'dayjs';
import SmartItemPicker from '@/components/SmartItemPicker.vue';
import { itemAssociationApi } from '@/api/resources';
import type { ItemAssociationEntry } from '@/types/common';

/** 面板中一个可管理的关联分组。 */
export interface AssociationPanelSection {
  /** 标准关联类型（枚举名） */
  relationType: 'DATA_ELEMENT_MEANING' | 'DATA_ELEMENT_DOMAIN' | 'VALUE_DOMAIN_MEANING' | 'DATA_ELEMENT_CONCEPT_DOMAIN';
  /** API 路径值（kebab-case） */
  pathType: string;
  /** 展示分组匹配的方向（相对焦点实体） */
  direction: 'OUTGOING' | 'INCOMING';
  /** 变更请求中焦点实体担任的标准方向角色 */
  focusRole: 'source' | 'target';
  /** 分组标题（目标实体类型名，如「数据元」；正反向一视同仁） */
  title: string;
  /** 添加 picker 的类型过滤 */
  targetItemTypes: string[];
  addPlaceholder: string;
}

const props = defineProps<{
  /** 焦点实体 Item.id；新建态为 null */
  itemId?: string | null;
  sections: AssociationPanelSection[];
}>();

const emit = defineEmits<{ (e: 'changed'): void }>();

const associations = ref<ItemAssociationEntry[]>([]);
const loading = ref(false);
const mutating = ref(false);
const addValues = reactive<Record<string, string[] | null>>({});

function sectionKey(section: AssociationPanelSection): string {
  return section.relationType + '-' + section.direction;
}

function entriesOf(section: AssociationPanelSection): ItemAssociationEntry[] {
  return associations.value.filter(
    (a) => a.relationType === section.relationType && a.direction === section.direction
  );
}

function fmt(v?: string | null): string {
  if (!v) return '';
  const d = dayjs(v);
  return d.isValid() ? d.format('YYYY-MM-DD HH:mm') : '';
}

async function refresh() {
  if (!props.itemId) {
    associations.value = [];
    return;
  }
  loading.value = true;
  try {
    const res = await itemAssociationApi.list(props.itemId);
    associations.value = res.associations || [];
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '加载关联失败');
  } finally {
    loading.value = false;
  }
}

/** 按关联类型的标准方向把 (焦点, 对方) 映射为 (source, target)。 */
function mutationIds(section: AssociationPanelSection, otherItemId: string) {
  return section.focusRole === 'target'
    ? { sourceItemId: otherItemId, targetItemId: props.itemId as string }
    : { sourceItemId: props.itemId as string, targetItemId: otherItemId };
}

function selectionOf(section: AssociationPanelSection): string[] {
  const v = addValues[sectionKey(section)];
  return Array.isArray(v) ? v.filter(Boolean) : [];
}

/** 批量建立关联：一次可添加多个对象（0..* 侧）。 */
async function onAdd(section: AssociationPanelSection) {
  const picked = selectionOf(section);
  if (!picked.length || !props.itemId) return;
  mutating.value = true;
  try {
    const results = await Promise.allSettled(
      picked.map((id) => {
        const ids = mutationIds(section, id);
        return itemAssociationApi.associate(section.pathType, ids.sourceItemId, ids.targetItemId);
      })
    );
    const failed = results.filter((r) => r.status === 'rejected').length;
    if (failed) {
      ElMessage.warning(`已建立 ${picked.length - failed} 个关联，${failed} 个失败`);
    } else {
      ElMessage.success('已建立关联');
    }
    addValues[sectionKey(section)] = null;
    await refresh();
    emit('changed');
  } finally {
    mutating.value = false;
  }
}

async function onRemove(section: AssociationPanelSection, row: ItemAssociationEntry) {
  if (!props.itemId) return;
  mutating.value = true;
  try {
    const ids = mutationIds(section, row.itemId);
    await itemAssociationApi.dissociate(section.pathType, ids.sourceItemId, ids.targetItemId);
    ElMessage.success('已解除关联');
    await refresh();
    emit('changed');
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '解除关联失败');
  } finally {
    mutating.value = false;
  }
}

watch(() => props.itemId, () => refresh(), { immediate: true });
</script>

<style scoped>
.assoc-section {
  margin-bottom: 12px;
}
.assoc-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.assoc-identifier {
  font-family: ui-monospace, SFMono-Regular, monospace;
  font-weight: 600;
}
.assoc-add {
  display: flex;
  gap: 8px;
  margin-top: 8px;
  align-items: flex-start;
}
.assoc-add > :first-child {
  flex: 1;
}
</style>
