<template>
  <div class="multilingual-editor">
    <div
      v-for="(row, index) in modelValue"
      :key="index"
      class="multilingual-card"
    >
      <div class="multilingual-row">
        <el-select
          v-model="row.languageId"
          :placeholder="$t('multilingual.languagePlaceholder')"
          style="width: 140px; flex-shrink: 0;"
          size="small"
        >
          <el-option
            v-for="lang in languages"
            :key="lang.id"
            :label="lang.languageIdentifier"
            :value="lang.id"
          />
        </el-select>
        <el-input
          v-model="row.value"
          :placeholder="$t('multilingual.valuePlaceholder', { label })"
          size="small"
        />
        <el-select
          v-model="row.sourceId"
          :placeholder="$t('multilingual.sourcePlaceholder')"
          size="small"
          style="width: 160px; flex-shrink: 0;"
          clearable
        >
          <el-option
            v-for="doc in referenceDocuments"
            :key="doc.id"
            :label="doc.title || doc.identifier || doc.id.slice(0, 8)"
            :value="doc.id"
          />
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

      <div class="context-section">
        <div class="context-header" @click="toggleExpand(index)">
          <el-icon size="12"><component :is="expanded[index] ? ArrowDown : ArrowRight" /></el-icon>
          <span>{{ $t('multilingual.contextAssociation') }}</span>
          <el-tag v-if="row.contexts?.length" size="small" type="info" round style="margin-left: 8px;">
            {{ row.contexts.length }}
          </el-tag>
          <el-tag
            v-if="namingConventionOptions && namingConventionOptions.length > 0"
            size="small"
            :type="(row.namingConventionIds || []).length > 0 ? 'success' : (requireNamingConventions ? 'danger' : 'info')"
            round
            style="margin-left: 8px;"
          >
            {{ $t('multilingual.namingConvention') }} {{ (row.namingConventionIds || []).length }}
          </el-tag>
        </div>
        <el-collapse-transition>
          <div v-show="expanded[index]" class="context-body">
            <div
              v-for="(ctx, cidx) in (row.contexts || [])"
              :key="cidx"
              class="context-row"
            >
              <el-select
                v-model="ctx.contextId"
                :placeholder="$t('multilingual.contextPlaceholder')"
                size="small"
                style="width: 180px;"
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
                v-model="ctx.acceptability"
                :placeholder="$t('multilingual.acceptabilityPlaceholder')"
                size="small"
                style="width: 140px;"
              >
                <el-option :label="$t('acceptability.PREFERRED')" value="PREFERRED" />
                <el-option :label="$t('acceptability.ADMITTED')" value="ADMITTED" />
                <el-option :label="$t('acceptability.DEPRECATED')" value="DEPRECATED" />
                <el-option :label="$t('acceptability.SUPERSEDED')" value="SUPERSEDED" />
                <el-option :label="$t('acceptability.OBSOLETE')" value="OBSOLETE" />
              </el-select>
              <el-button
                type="danger"
                size="small"
                text
                circle
                @click="removeContext(index, cidx)"
              >
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
            <div v-if="namingConventionOptions && namingConventionOptions.length > 0" class="naming-convention-row">
              <span class="nc-label">{{ $t('multilingual.namingConvention') }}</span>
              <el-select
                v-model="row.namingConventionIds"
                multiple
                collapse-tags
                collapse-tags-tooltip
                :placeholder="$t('multilingual.namingConventionPlaceholder')"
                size="small"
                style="flex: 1;"
                :class="{ 'is-error': requireNamingConventions && !(row.namingConventionIds || []).length }"
              >
                <el-option
                  v-for="nc in namingConventionOptions"
                  :key="nc.id"
                  :label="nc.label || nc.scopeRule || nc.id.slice(0, 8)"
                  :value="nc.id"
                />
              </el-select>
            </div>
            <el-button
              type="primary"
              size="small"
              text
              @click="addContext(index)"
            >
              <el-icon><Plus /></el-icon>
              {{ $t('multilingual.addContext') }}
            </el-button>
          </div>
        </el-collapse-transition>
      </div>
    </div>
    <el-button
      type="primary"
      size="small"
      text
      @click="addRow"
    >
      <el-icon><Plus /></el-icon>
      {{ $t('multilingual.addLabel', { label }) }}
    </el-button>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { Plus, Delete, ArrowRight, ArrowDown } from '@element-plus/icons-vue';
import { languageApi, contextApi, referenceDocumentApi } from '@/api/resources';
import type { TextLineForm, NamingConventionResponse } from '@/types/common';

interface Props {
  modelValue: TextLineForm[];
  label: string;
  namingConventionOptions?: NamingConventionResponse[];
  requireNamingConventions?: boolean;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  'update:modelValue': [value: TextLineForm[]];
}>();

const languages = ref<{ id: string; languageIdentifier: string }[]>([]);
const contextOptions = ref<{ id: string; label: string }[]>([]);
const referenceDocuments = ref<{ id: string; title?: string; identifier?: string }[]>([]);
const searchingContexts = ref(false);
const expanded = ref<Record<number, boolean>>({});

onMounted(async () => {
  try {
    const langRes = await languageApi.list({ size: 100 });
    languages.value = (langRes.content || []).map((l: any) => ({
      id: l.id,
      languageIdentifier: l.languageIdentifier || l.identifier || '',
    }));
    await searchContexts('');
  } catch {
    // fallback
  }
  try {
    const refRes = await referenceDocumentApi.list({ size: 200 });
    referenceDocuments.value = (refRes.content || []).map((d: any) => ({
      id: d.id,
      title: d.title,
      identifier: d.identifier,
    }));
  } catch {
    // fallback
  }
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

function toggleExpand(index: number) {
  expanded.value[index] = !expanded.value[index];
}

function addRow() {
  const next = [...props.modelValue, { languageId: '', value: '' }];
  emit('update:modelValue', next);
}

function removeRow(index: number) {
  const next = props.modelValue.filter((_, i) => i !== index);
  emit('update:modelValue', next);
}

function addContext(rowIndex: number) {
  const next = [...props.modelValue];
  if (!next[rowIndex].contexts) {
    next[rowIndex].contexts = [];
  }
  next[rowIndex].contexts!.push({ contextId: '', acceptability: 'ADMITTED' });
  emit('update:modelValue', next);
  expanded.value[rowIndex] = true;
}

function removeContext(rowIndex: number, ctxIndex: number) {
  const next = [...props.modelValue];
  next[rowIndex].contexts!.splice(ctxIndex, 1);
  emit('update:modelValue', next);
}
</script>

<style scoped>
.multilingual-editor {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.multilingual-card {
  background: #f8fafc;
  border-radius: 8px;
  padding: 10px 12px;
  border: 1px solid #f1f5f9;
}
.multilingual-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.context-section {
  margin-top: 8px;
}
.context-header {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #6b7280;
  cursor: pointer;
  user-select: none;
  padding: 2px 0;
}
.context-header:hover {
  color: #2563eb;
}
.context-body {
  margin-top: 8px;
  padding-left: 8px;
}
.context-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.naming-convention-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed #e2e8f0;
}
.nc-label {
  font-size: 12px;
  color: #6b7280;
  white-space: nowrap;
}
.is-error .el-input__wrapper {
  box-shadow: 0 0 0 1px #f56c6c inset;
}
</style>
*** End of File
