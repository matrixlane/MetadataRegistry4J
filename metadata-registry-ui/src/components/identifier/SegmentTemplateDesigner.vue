<template>
  <div class="segment-designer">
    <!-- 预设库 -->
    <div class="designer-toolbar">
      <el-select
        v-model="presetKey"
        placeholder="从预设库载入（WS/T 363 等）"
        clearable
        style="width: 280px;"
        @change="loadPreset"
      >
        <el-option v-for="p in presets" :key="p.key" :label="p.name" :value="p.key">
          <div>{{ p.name }}</div>
          <div style="font-size: 12px; color: var(--el-text-color-secondary);">{{ p.description }}</div>
        </el-option>
      </el-select>
      <span v-if="currentPresetDescription" class="preset-desc">{{ currentPresetDescription }}</span>
    </div>

    <!-- 段块条 -->
    <div class="segment-strip">
      <div
        v-for="(seg, i) in segments"
        :key="i"
        class="segment-chip"
        :class="{ active: selectedIndex === i }"
        @click="selectedIndex = i"
      >
        <span class="chip-type">{{ typeLabel(seg.type) }}</span>
        <span class="chip-summary">{{ summarize(seg) }}</span>
        <span class="chip-actions" @click.stop>
          <el-icon :class="{ disabled: i === 0 }" @click="move(i, -1)"><ArrowUp /></el-icon>
          <el-icon :class="{ disabled: i === segments.length - 1 }" @click="move(i, 1)"><ArrowDown /></el-icon>
          <el-icon @click="remove(i)"><Close /></el-icon>
        </span>
      </div>
      <el-dropdown v-if="segments.length === 0 || true" trigger="click">
        <el-button size="small" type="primary" plain>
          <el-icon style="margin-right: 4px;"><Plus /></el-icon>添加段
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item v-for="t in palette" :key="t.type" @click="addSegment(t.type)">
              {{ t.label }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <!-- 参数面板 -->
    <div v-if="selected" class="params-panel">
      <template v-if="selected.type === 'LITERAL'">
        <el-form-item label="固定字符串" required>
          <el-input v-model="selected.params.value" placeholder="例如：CO" @input="emitChange" />
        </el-form-item>
      </template>

      <template v-else-if="selected.type === 'OBJECT_ABBREVIATION'">
        <el-form-item label="段名">
          <el-input v-model="selected.name" placeholder="objectAbbr" @input="emitChange" />
        </el-form-item>
        <el-form-item label="对象类型缩写映射" required>
          <div v-for="(abbr, type) in selected.params.mapping" :key="type" class="mapping-row">
            <el-input :model-value="type" disabled style="width: 180px;" />
            <span>→</span>
            <el-input v-model="selected.params.mapping[type]" style="width: 120px;" @input="emitChange" />
            <el-icon class="row-remove" @click="removeMapping(String(type))"><Close /></el-icon>
          </div>
          <div class="mapping-row">
            <el-input v-model="newMappingType" placeholder="对象类型，如 DataElement" style="width: 180px;" />
            <span>→</span>
            <el-input v-model="newMappingAbbr" placeholder="缩写，如 DE" style="width: 120px;" />
            <el-button size="small" @click="addMapping">添加</el-button>
          </div>
        </el-form-item>
        <el-form-item label="默认缩写">
          <el-input v-model="selected.params.default" placeholder="未命中映射时使用" @input="emitChange" />
        </el-form-item>
      </template>

      <template v-else-if="selected.type === 'SEQUENCE'">
        <el-form-item label="段名">
          <el-input v-model="selected.name" placeholder="seq" @input="emitChange" />
        </el-form-item>
        <div class="params-grid">
          <el-form-item label="起始值">
            <el-input-number v-model="selected.params.start" :min="0" @change="emitChange" />
          </el-form-item>
          <el-form-item label="步长">
            <el-input-number v-model="selected.params.increment" :min="1" @change="emitChange" />
          </el-form-item>
          <el-form-item label="补零位数">
            <el-input-number v-model="selected.params.pad" :min="1" :max="18" @change="emitChange" />
          </el-form-item>
        </div>
        <el-form-item label="作用域引用（scopeRefs）">
          <el-select
            v-model="selected.params.scopeRefs"
            multiple
            placeholder="引用前面的变量/日期段；空 = 命名空间级全局计数"
            style="width: 100%;"
            @change="emitChange"
          >
            <el-option v-for="n in referenceableNames" :key="n" :label="n" :value="n" />
          </el-select>
        </el-form-item>
      </template>

      <template v-else-if="selected.type === 'UUID'">
        <el-form-item label="形式">
          <el-radio-group v-model="selected.params.form" @change="emitChange">
            <el-radio-button value="full">完整 UUID</el-radio-button>
            <el-radio-button value="short">短型（8 位）</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </template>

      <template v-else-if="selected.type === 'SEPARATOR'">
        <el-form-item label="分隔符" required>
          <el-radio-group v-model="selected.params.char" @change="emitChange">
            <el-radio-button v-for="c in separatorChars" :key="c" :value="c">{{ c }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </template>

      <template v-else-if="selected.type === 'TIMESTAMP'">
        <el-form-item label="段名">
          <el-input v-model="selected.name" placeholder="month" @input="emitChange" />
        </el-form-item>
        <el-form-item label="时间格式" required>
          <el-input v-model="selected.params.pattern" placeholder="yyyyMMdd" @input="emitChange" />
        </el-form-item>
      </template>

      <template v-else-if="selected.type === 'CHECKSUM'">
        <el-form-item label="校验算法">
          <el-radio-group v-model="selected.params.algorithm" @change="emitChange">
            <el-radio-button value="luhn">Luhn</el-radio-button>
            <el-radio-button value="mod11">MOD 11</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </template>

      <template v-else-if="selected.type === 'VARIABLE'">
        <el-form-item label="变量名（注册请求键）" required>
          <el-input v-model="selected.name" placeholder="majorCategory" @input="emitChange" />
        </el-form-item>
        <el-form-item label="提示标签">
          <el-input v-model="selected.params.label" placeholder="大类编码" @input="emitChange" />
        </el-form-item>
        <el-form-item label="校验正则" required>
          <el-input v-model="selected.params.pattern" placeholder="\d{2}" @input="emitChange" />
        </el-form-item>
        <div class="params-grid">
          <el-form-item label="补零位数">
            <el-input-number v-model="selected.params.pad" :min="0" :max="18" @change="emitChange" />
          </el-form-item>
          <el-form-item label="默认值">
            <el-input v-model="selected.params.default" @input="emitChange" />
          </el-form-item>
          <el-form-item label="必填">
            <el-switch v-model="selected.params.required" @change="emitChange" />
          </el-form-item>
        </div>
      </template>

      <template v-else-if="selected.type === 'RANDOM'">
        <el-form-item label="长度">
          <el-input-number v-model="selected.params.length" :min="1" :max="64" @change="emitChange" />
        </el-form-item>
        <el-form-item label="字符集">
          <el-input v-model="selected.params.alphabet" placeholder="默认数字+大写字母" @input="emitChange" />
        </el-form-item>
      </template>
    </div>

    <!-- 实时预览与校验 -->
    <div class="preview-bar">
      <span class="preview-label">实时预览</span>
      <code class="preview-sample">{{ previewSample || '（无法生成预览）' }}</code>
      <el-tooltip v-if="formatRegex" content="派生的格式校验正则" placement="top">
        <code class="preview-regex">{{ formatRegex }}</code>
      </el-tooltip>
    </div>
    <el-alert
      v-for="(err, i) in validationErrors"
      :key="i"
      :title="err"
      type="error"
      :closable="false"
      show-icon
      style="margin-top: 6px;"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { ArrowDown, ArrowUp, Close, Plus } from '@element-plus/icons-vue';
import { identifierStrategyApi, type StrategyPreset } from '@/api/identifierStrategy';

interface SegmentDef {
  type: string;
  name?: string;
  params: Record<string, any>;
}

const props = defineProps<{
  modelValue: string;
  /** 有命名空间时走后端预览（更权威），否则本地简化预览 */
  namespaceId?: string;
}>();

const emit = defineEmits<{ 'update:modelValue': [value: string] }>();

const palette = [
  { type: 'LITERAL', label: '固定字符串' },
  { type: 'OBJECT_ABBREVIATION', label: '元数据对象缩写' },
  { type: 'SEQUENCE', label: '顺序号' },
  { type: 'UUID', label: 'UUID' },
  { type: 'SEPARATOR', label: '分隔符' },
  { type: 'TIMESTAMP', label: '时间戳' },
  { type: 'CHECKSUM', label: '校验位' },
  { type: 'VARIABLE', label: '输入变量' },
  { type: 'RANDOM', label: '随机' },
];

const separatorChars = ['.', '-', '_', '/', ':'];

const segments = ref<SegmentDef[]>([]);
const selectedIndex = ref(-1);
const presets = ref<StrategyPreset[]>([]);
const presetKey = ref<string>();
const previewSample = ref('');
const formatRegex = ref('');
const validationErrors = ref<string[]>([]);
const newMappingType = ref('');
const newMappingAbbr = ref('');

const selected = computed(() =>
  selectedIndex.value >= 0 && selectedIndex.value < segments.value.length
    ? segments.value[selectedIndex.value]
    : null,
);

const currentPresetDescription = computed(
  () => presets.value.find((p) => p.key === presetKey.value)?.description || '',
);

const referenceableNames = computed(() =>
  segments.value
    .slice(0, Math.max(selectedIndex.value, 0))
    .filter((s) => s.name && (s.type === 'VARIABLE' || s.type === 'TIMESTAMP'))
    .map((s) => s.name as string),
);

function typeLabel(type: string): string {
  return palette.find((p) => p.type === type)?.label || type;
}

function summarize(seg: SegmentDef): string {
  switch (seg.type) {
    case 'LITERAL': return seg.params.value || '';
    case 'OBJECT_ABBREVIATION': return seg.name || '对象缩写';
    case 'SEQUENCE': return `${seg.name || 'seq'}(pad=${seg.params.pad ?? 6})`;
    case 'UUID': return seg.params.form === 'short' ? '短型' : '完整';
    case 'SEPARATOR': return seg.params.char || '';
    case 'TIMESTAMP': return seg.params.pattern || '';
    case 'CHECKSUM': return seg.params.algorithm || 'luhn';
    case 'VARIABLE': return seg.params.label || seg.name || '';
    case 'RANDOM': return `len=${seg.params.length ?? 8}`;
    default: return '';
  }
}

function defaultSegment(type: string): SegmentDef {
  switch (type) {
    case 'LITERAL': return { type, params: { value: 'CO' } };
    case 'OBJECT_ABBREVIATION':
      return { type, name: 'objectAbbr', params: { mapping: { DataElement: 'DE', ValueDomain: 'VD' } } };
    case 'SEQUENCE': return { type, name: 'seq', params: { start: 1, increment: 1, pad: 3, scopeRefs: [], overflow: 'REJECT' } };
    case 'UUID': return { type, params: { form: 'full' } };
    case 'SEPARATOR': return { type, params: { char: '.' } };
    case 'TIMESTAMP': return { type, name: 'date', params: { pattern: 'yyyyMMdd' } };
    case 'CHECKSUM': return { type, params: { algorithm: 'luhn' } };
    case 'VARIABLE':
      return { type, name: `var${segments.value.length + 1}`, params: { label: '', pattern: '\\w+', pad: 0, default: '', required: true } };
    case 'RANDOM': return { type, params: { length: 8, alphabet: '' } };
    default: return { type, params: {} };
  }
}

function addSegment(type: string) {
  segments.value.push(defaultSegment(type));
  selectedIndex.value = segments.value.length - 1;
  emitChange();
}

function remove(i: number) {
  segments.value.splice(i, 1);
  if (selectedIndex.value >= segments.value.length) {
    selectedIndex.value = segments.value.length - 1;
  }
  emitChange();
}

function move(i: number, delta: number) {
  const j = i + delta;
  if (j < 0 || j >= segments.value.length) return;
  const [seg] = segments.value.splice(i, 1);
  segments.value.splice(j, 0, seg);
  selectedIndex.value = j;
  emitChange();
}

function addMapping() {
  if (!selected.value || !newMappingType.value.trim() || !newMappingAbbr.value.trim()) return;
  selected.value.params.mapping = {
    ...(selected.value.params.mapping || {}),
    [newMappingType.value.trim()]: newMappingAbbr.value.trim(),
  };
  newMappingType.value = '';
  newMappingAbbr.value = '';
  emitChange();
}

function removeMapping(type: string) {
  if (!selected.value?.params.mapping) return;
  delete selected.value.params.mapping[type];
  emitChange();
}

function emitChange() {
  emit('update:modelValue', JSON.stringify({ segments: segments.value }));
  schedulePreview();
}

async function loadPreset(key?: string) {
  const preset = presets.value.find((p) => p.key === key);
  if (!preset) return;
  applyConfigJson(preset.configJson);
  emitChange();
}

function applyConfigJson(json: string) {
  try {
    const parsed = JSON.parse(json);
    segments.value = (parsed.segments || []).map((s: any) => ({
      type: s.type,
      name: s.name,
      params: s.params || {},
    }));
    selectedIndex.value = segments.value.length ? 0 : -1;
  } catch {
    segments.value = [];
    selectedIndex.value = -1;
  }
}

/** 样例变量值：默认值优先，否则按补零位数给 "1"/"01"。 */
function sampleVariables(): Record<string, string> {
  const vars: Record<string, string> = {};
  for (const seg of segments.value) {
    if (seg.type === 'VARIABLE' && seg.name) {
      const pad = seg.params.pad || 0;
      let v = seg.params.default || '1';
      if (pad > 0) v = v.padStart(pad, '0');
      if (!seg.params.default && (seg.params.pattern || '').includes('{2}')) v = '01';
      vars[seg.name] = v;
    }
  }
  return vars;
}

let previewTimer: ReturnType<typeof setTimeout> | undefined;
function schedulePreview() {
  if (previewTimer) clearTimeout(previewTimer);
  previewTimer = setTimeout(refreshPreview, 300);
}

async function refreshPreview() {
  const configJson = JSON.stringify({ segments: segments.value });
  if (segments.value.length === 0) {
    previewSample.value = '';
    formatRegex.value = '';
    validationErrors.value = [];
    return;
  }
  if (props.namespaceId) {
    try {
      const res = await identifierStrategyApi.preview(
        props.namespaceId, configJson, sampleVariables(), 'DataElement');
      previewSample.value = res.sample || '';
      formatRegex.value = res.formatRegex || '';
      validationErrors.value = res.validationErrors || [];
      return;
    } catch {
      // 后端不可用时退化为本地预览
    }
  }
  localPreview();
}

/** 无命名空间上下文（如初始化向导）时的简化本地预览。 */
function localPreview() {
  const vars = sampleVariables();
  let out = '';
  const errors: string[] = [];
  for (const seg of segments.value) {
    try {
      switch (seg.type) {
        case 'LITERAL': out += seg.params.value || ''; break;
        case 'OBJECT_ABBREVIATION':
          out += (seg.params.mapping || {}).DataElement || seg.params.default || '';
          break;
        case 'SEQUENCE': {
          const start = seg.params.start ?? 1;
          const pad = seg.params.pad ?? 6;
          out += String(start).padStart(pad, '0');
          break;
        }
        case 'UUID': out += seg.params.form === 'short' ? 'a1b2c3d4' : 'a1b2c3d4-e5f6-7890-abcd-ef1234567890'; break;
        case 'SEPARATOR': out += seg.params.char || ''; break;
        case 'TIMESTAMP': out += (seg.params.pattern || '').replace(/y/g, '2').replace(/M|d|H|m|s/g, '0'); break;
        case 'CHECKSUM': out += '0'; break;
        case 'VARIABLE': out += vars[seg.name || ''] || ''; break;
        case 'RANDOM': out += 'X'.repeat(seg.params.length ?? 8); break;
      }
    } catch (e: any) {
      errors.push(e?.message || String(e));
    }
  }
  previewSample.value = out;
  formatRegex.value = '';
  validationErrors.value = errors;
}

watch(() => props.modelValue, (v) => {
  if (v && v !== JSON.stringify({ segments: segments.value })) {
    applyConfigJson(v);
    schedulePreview();
  }
});

onMounted(async () => {
  if (props.modelValue) {
    applyConfigJson(props.modelValue);
  }
  schedulePreview();
  try {
    presets.value = await identifierStrategyApi.presets();
  } catch {
    presets.value = [];
  }
});
</script>

<style scoped>
.segment-designer {
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  padding: 12px;
}
.designer-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.preset-desc {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.segment-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
}
.segment-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  padding: 4px 8px;
  cursor: pointer;
  background: var(--el-fill-color-light);
}
.segment-chip.active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
.chip-type {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.chip-summary {
  font-family: monospace;
  font-size: 13px;
}
.chip-actions {
  display: inline-flex;
  gap: 2px;
}
.chip-actions .el-icon {
  cursor: pointer;
  font-size: 12px;
}
.chip-actions .el-icon.disabled {
  opacity: 0.3;
  cursor: not-allowed;
}
.params-panel {
  border-top: 1px dashed var(--el-border-color);
  padding-top: 12px;
  margin-bottom: 12px;
}
.params-grid {
  display: flex;
  gap: 20px;
  flex-wrap: wrap;
}
.mapping-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.row-remove {
  cursor: pointer;
}
.preview-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  border-top: 1px dashed var(--el-border-color);
  padding-top: 10px;
}
.preview-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.preview-sample {
  font-size: 15px;
  font-weight: 600;
}
.preview-regex {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}
</style>
