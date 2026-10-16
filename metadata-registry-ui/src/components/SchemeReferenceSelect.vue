<template>
  <el-select
    :model-value="modelValue ?? null"
    filterable
    remote
    clearable
    :remote-method="search"
    :loading="loading"
    placeholder="搜索参考文档（可选）"
    style="width: 100%;"
    data-test="scheme-reference-select"
    @update:model-value="onChange"
  >
    <el-option
      v-for="d in candidates"
      :key="d.id"
      :label="d.title || d.identifier || d.id"
      :value="d.id"
    >
      <span class="scheme-ref-option">
        <span>{{ d.identifier || '—' }}</span>
        <span v-if="d.title" class="scheme-ref-option-title">{{ d.title }}</span>
      </span>
    </el-option>
  </el-select>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
import { referenceDocumentApi } from '../api/resources';
import type { ReferenceDocument } from '../types/common';

const props = defineProps<{ modelValue?: string | null }>();
const emit = defineEmits<{ (e: 'update:modelValue', v: string | null): void }>();

const candidates = ref<ReferenceDocument[]>([]);
const loading = ref(false);

async function search(keyword: string): Promise<ReferenceDocument[]> {
  loading.value = true;
  try {
    const res = await referenceDocumentApi.list({ page: 0, size: 50, q: keyword });
    // 响应拦截器已拆出 ApiResponse.data，这里拿到的就是分页对象本身。
    const items = ((res as any)?.content ?? []) as ReferenceDocument[];
    candidates.value = items;
    return items;
  } finally {
    loading.value = false;
  }
}

/** 编辑态：已选文档不在前 50 条候选里时按 id 反查详情并补进候选，避免选择框只显示原始 id。 */
async function ensureSelectedVisible(id: string | null | undefined) {
  if (!id) return;
  if (candidates.value.some((d) => d.id === id)) return;
  try {
    const res = (await referenceDocumentApi.get(id)) as ReferenceDocument | null;
    if (res?.id) candidates.value = [res, ...candidates.value];
  } catch {
    // 详情反查失败时保留原始 id 展示
  }
}

function onChange(v: string | null) {
  emit('update:modelValue', v || null);
}

onMounted(() => {
  void search('').then(() => ensureSelectedVisible(props.modelValue));
});
watch(() => props.modelValue, ensureSelectedVisible);
</script>

<style scoped>
.scheme-ref-option {
  display: flex;
  gap: 8px;
}
.scheme-ref-option-title {
  color: var(--el-text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
