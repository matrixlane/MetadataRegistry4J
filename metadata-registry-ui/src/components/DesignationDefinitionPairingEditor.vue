<template>
  <div class="pairing-editor">
    <div v-if="!designations.length || !definitions.length" class="pairing-empty">
      <el-empty description="请先添加指称和定义，再创建配对" />
    </div>
    <div v-else class="pairing-list">
      <div
        v-for="(row, index) in modelValue"
        :key="index"
        class="pairing-card"
      >
        <div class="pairing-row">
          <el-select
            v-model="row.designationId"
            placeholder="选择指称"
            size="small"
            style="flex: 1;"
            filterable
          >
            <el-option
              v-for="d in designations"
              :key="d.id"
              :label="d.value"
              :value="d.id"
            />
          </el-select>
          <el-icon class="pairing-link"><Link /></el-icon>
          <el-select
            v-model="row.definitionId"
            placeholder="选择定义"
            size="small"
            style="flex: 1;"
            filterable
          >
            <el-option
              v-for="d in definitions"
              :key="d.id"
              :label="d.value"
              :value="d.id"
            />
          </el-select>
        </div>
        <div class="pairing-row" style="margin-top: 8px;">
          <el-select
            v-model="row.contextId"
            placeholder="选择上下文（可选）"
            size="small"
            style="flex: 1;"
            clearable
            filterable
            remote
            reserve-keyword
            :remote-method="searchContexts"
            :loading="searchingContexts"
          >
            <el-option
              v-for="c in contextOptions"
              :key="c.id"
              :label="c.label"
              :value="c.id"
            />
          </el-select>
          <el-select
            v-model="row.acceptability"
            placeholder="可接受性"
            size="small"
            style="width: 140px;"
          >
            <el-option label="首选" value="PREFERRED" />
            <el-option label="认可" value="ADMITTED" />
            <el-option label="已弃用" value="DEPRECATED" />
            <el-option label="已取代" value="SUPERSEDED" />
            <el-option label="已过时" value="OBSOLETE" />
          </el-select>
          <el-button
            type="danger"
            size="small"
            text
            circle
            @click="removeRow(index)"
          >
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>
      </div>
      <el-button
        type="primary"
        size="small"
        text
        @click="addRow"
      >
        <el-icon><Plus /></el-icon>
        添加配对
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Plus, Delete, Link } from '@element-plus/icons-vue';
import { contextApi } from '@/api/resources';
import type { PairingForm } from '@/types/common';
import { ref, onMounted } from 'vue';

interface Props {
  modelValue: PairingForm[];
  designations: { id: string; value: string }[];
  definitions: { id: string; value: string }[];
}

const props = defineProps<Props>();
const emit = defineEmits<{
  'update:modelValue': [value: PairingForm[]];
}>();

const contextOptions = ref<{ id: string; label: string }[]>([]);
const searchingContexts = ref(false);

onMounted(async () => {
  await searchContexts('');
});

async function searchContexts(query: string) {
  searchingContexts.value = true;
  try {
    const res = await contextApi.options({ q: query });
    contextOptions.value = (res as any) || [];
  } catch {
    // ignore
  } finally {
    searchingContexts.value = false;
  }
}

function addRow() {
  const next: PairingForm[] = [...props.modelValue, {
    designationId: '',
    definitionId: '',
    contextId: undefined,
    acceptability: 'ADMITTED',
  }];
  emit('update:modelValue', next);
}

function removeRow(index: number) {
  const next = props.modelValue.filter((_, i) => i !== index);
  emit('update:modelValue', next);
}
</script>

<style scoped>
.pairing-editor {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.pairing-empty {
  padding: 24px 0;
}
.pairing-card {
  background: #f8fafc;
  border-radius: 8px;
  padding: 12px;
  border: 1px solid #f1f5f9;
}
.pairing-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.pairing-link {
  color: #9ca3af;
  flex-shrink: 0;
}
</style>
