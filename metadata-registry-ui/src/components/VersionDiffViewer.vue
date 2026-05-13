<template>
  <div class="diff-viewer">
    <div v-if="!diff.hasDifferences" class="no-diff">
      <el-empty description="两个版本之间没有差异" />
    </div>
    <div v-else>
      <el-alert
        :title="`对比 v${diff.baseVersion} → v${diff.compareVersion}`"
        type="info"
        :closable="false"
        class="diff-header"
      />
      <el-table :data="diff.fields" size="small" stripe>
        <el-table-column prop="fieldName" label="字段" width="200">
          <template #default="{ row }">
            <span class="field-name">{{ row.fieldLabel || row.fieldName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="type" label="变更类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="diffTagType(row.type)">
              {{ diffTypeLabel(row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="旧值" min-width="200">
          <template #default="{ row }">
            <div v-if="row.type === 'ADDED'" class="diff-empty">—</div>
            <div v-else class="diff-old">
              <ValueDiff :value="row.oldValue" />
            </div>
          </template>
        </el-table-column>
        <el-table-column label="新值" min-width="200">
          <template #default="{ row }">
            <div v-if="row.type === 'REMOVED'" class="diff-empty">—</div>
            <div v-else class="diff-new">
              <ValueDiff :value="row.newValue" />
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps<{
  diff: any;
}>();

function diffTagType(type: string): any {
  switch (type) {
    case 'ADDED': return 'success';
    case 'REMOVED': return 'danger';
    case 'MODIFIED': return 'warning';
    case 'UNCHANGED': return 'info';
    default: return undefined;
  }
}

function diffTypeLabel(type: string): string {
  switch (type) {
    case 'ADDED': return '新增';
    case 'REMOVED': return '删除';
    case 'MODIFIED': return '修改';
    case 'UNCHANGED': return '未变';
    default: return type;
  }
}
</script>

<script lang="ts">
import { defineComponent, h } from 'vue';

const ValueDiff = defineComponent({
  props: { value: { type: [String, Number, Boolean, Object, Array], default: null } },
  setup(props) {
    return () => {
      const v = props.value;
      if (v === null || v === undefined) {
        return h('span', { class: 'diff-null' }, 'null');
      }
      if (typeof v === 'string') {
        if (v.length > 200) {
          return h('details', {}, [
            h('summary', { class: 'diff-truncated' }, v.substring(0, 200) + '...'),
            h('pre', { class: 'diff-full-text' }, v),
          ]);
        }
        return h('span', {}, v);
      }
      if (typeof v === 'boolean' || typeof v === 'number') {
        return h('span', {}, String(v));
      }
      return h('pre', { class: 'diff-json' }, JSON.stringify(v, null, 2));
    };
  },
});

export { ValueDiff };
</script>

<style scoped lang="scss">
.diff-viewer {
  .diff-header {
    margin-bottom: 16px;
  }

  .no-diff {
    padding: 40px 0;
  }

  .field-name {
    font-family: 'Consolas', 'Courier New', monospace;
    font-size: 12px;
    color: #374151;
  }

  .diff-empty {
    color: #9ca3af;
  }

  .diff-old {
    color: #b91c1c;
    text-decoration: line-through;
    background: #fef2f2;
    padding: 4px 8px;
    border-radius: 4px;
  }

  .diff-new {
    color: #15803d;
    background: #f0fdf4;
    padding: 4px 8px;
    border-radius: 4px;
  }

  .diff-null {
    color: #9ca3af;
    font-style: italic;
  }

  .diff-truncated {
    cursor: pointer;
    color: #2563eb;
  }

  .diff-full-text {
    margin-top: 8px;
    background: #f8fafc;
    padding: 8px;
    border-radius: 4px;
    font-size: 12px;
    white-space: pre-wrap;
    word-break: break-word;
    max-height: 300px;
    overflow-y: auto;
  }

  .diff-json {
    background: #f8fafc;
    padding: 8px;
    border-radius: 4px;
    font-size: 11px;
    max-height: 200px;
    overflow-y: auto;
  }
}
</style>
