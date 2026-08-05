<template>
  <div class="segment-designer" :class="{ 'is-disabled': disabled }">
    <!-- 预设库 -->
    <div class="designer-toolbar">
      <el-select
        v-model="presetKey"
        placeholder="从预设库载入"
        clearable
        :disabled="disabled"
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

    <el-alert
      v-for="(note, i) in loadWarnings"
      :key="'warn-' + i"
      :title="note"
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: 8px;"
    />

    <!-- 只读模板表达式视图 -->
    <div class="expression-bar">
      <span class="expression-label">模板表达式</span>
      <code class="expression-text">{{ templateExpression || '（空模板）' }}</code>
    </div>

    <!-- 段块条 -->
    <div class="segment-strip">
      <div
        v-for="(seg, i) in segments"
        :key="i"
        class="segment-chip"
        :class="{ active: selectedIndex === i, uniqueness: isUniqueness(seg.type) }"
        @click="selectedIndex = i"
      >
        <span class="chip-type">{{ typeLabel(seg.type) }}</span>
        <span class="chip-summary">{{ summarize(seg) }}</span>
        <span v-if="!disabled" class="chip-actions" @click.stop>
          <el-icon :class="{ disabled: i === 0 }" @click="move(i, -1)"><ArrowUp /></el-icon>
          <el-icon :class="{ disabled: i === segments.length - 1 }" @click="move(i, 1)"><ArrowDown /></el-icon>
          <el-icon @click="remove(i)"><Close /></el-icon>
        </span>
      </div>
      <el-dropdown v-if="!disabled" trigger="click">
        <el-button size="small" type="primary" plain>
          <el-icon style="margin-right: 4px;"><Plus /></el-icon>添加段
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <template v-for="group in paletteGroups" :key="group.label">
              <el-dropdown-item disabled>
                <span class="palette-group-label">{{ group.label }}</span>
              </el-dropdown-item>
              <el-dropdown-item
                v-for="t in group.items"
                :key="t.type"
                @click="addSegment(t.type)"
              >
                <span class="palette-item">{{ t.label }}</span>
              </el-dropdown-item>
            </template>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <!-- 参数面板 -->
    <div v-if="selected" class="params-panel">
      <template v-if="selected.type === 'LITERAL'">
        <el-form-item label="固定字符串" required>
          <el-input v-model="selected.params.value" placeholder="例如：WN" :disabled="disabled" @input="emitChange" />
        </el-form-item>
      </template>

      <template v-else-if="selected.type === 'OBJECT_ABBREVIATION'">
        <el-form-item label="段名">
          <el-input v-model="selected.name" placeholder="objectAbbr" :disabled="disabled" @input="emitChange" />
        </el-form-item>
        <el-form-item label="对象缩写映射">
          <div class="abbr-block">
            <div class="abbr-tip">
              系统级映射表（服务端 object-abbreviations.json，如需调整请联系管理员）
            </div>
            <el-table :data="abbrRows" size="small" max-height="220" border>
              <el-table-column prop="type" label="对象类型" />
              <el-table-column prop="abbr" label="缩写" width="110" />
            </el-table>
          </div>
        </el-form-item>
        <el-form-item label="默认缩写">
          <el-input
            v-model="selected.params.default"
            placeholder="未映射类型时使用（留空则生成时报错）"
            :disabled="disabled"
            @input="emitChange"
          />
        </el-form-item>
      </template>

      <template v-else-if="selected.type === 'SEQUENCE'">
        <el-form-item label="段名">
          <el-input v-model="selected.name" placeholder="seq" :disabled="disabled" @input="emitChange" />
        </el-form-item>
        <div class="params-grid">
          <el-form-item label="起始值">
            <el-input-number v-model="selected.params.start" :min="0" :disabled="disabled" @change="emitChange" />
          </el-form-item>
          <el-form-item label="步长">
            <el-input-number v-model="selected.params.increment" :min="1" :disabled="disabled" @change="emitChange" />
          </el-form-item>
          <el-form-item label="补零位数">
            <el-input-number v-model="selected.params.pad" :min="1" :max="18" :disabled="disabled" @change="emitChange" />
          </el-form-item>
        </div>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="顺序号作用域自动推导：模板包含元数据对象缩写段时，按对象类型各自独立计数；否则在命名空间内全局计数。不支持手动指定作用域。"
        />
      </template>

      <template v-else-if="selected.type === 'UUID'">
        <el-form-item label="形式">
          <el-radio-group v-model="selected.params.form" :disabled="disabled" @change="emitChange">
            <el-radio-button value="full">完整 UUID</el-radio-button>
            <el-radio-button value="short">短型（8 位）</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </template>

      <template v-else-if="selected.type === 'SEPARATOR'">
        <el-form-item label="分隔符" required>
          <el-radio-group v-model="selected.params.char" :disabled="disabled" @change="emitChange">
            <el-radio-button v-for="c in separatorChars" :key="c" :value="c">{{ c }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </template>

      <template v-else-if="selected.type === 'TIMESTAMP'">
        <el-form-item label="段名">
          <el-input v-model="selected.name" placeholder="ts" :disabled="disabled" @input="emitChange" />
        </el-form-item>
        <el-form-item label="时间格式" required>
          <el-select
            v-model="selected.params.pattern"
            filterable
            allow-create
            default-first-option
            placeholder="选择预设或输入自定义格式"
            :disabled="disabled"
            style="width: 100%;"
            @change="emitChange"
          >
            <el-option
              v-for="p in timestampPresets"
              :key="p.value"
              :label="p.label"
              :value="p.value"
            />
          </el-select>
        </el-form-item>
        <el-alert
          v-if="timestampPrecisionError"
          type="error"
          :closable="false"
          show-icon
          :title="timestampPrecisionError"
        />
      </template>

      <template v-else-if="selected.type === 'CHECKSUM'">
        <el-form-item label="校验算法">
          <el-radio-group v-model="selected.params.algorithm" :disabled="disabled" @change="emitChange">
            <el-radio-button value="luhn">Luhn</el-radio-button>
            <el-radio-button value="mod11">MOD 11</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </template>

      <template v-else-if="selected.type === 'VARIABLE'">
        <el-form-item label="变量名（注册请求键）" required>
          <el-input v-model="selected.name" placeholder="deptCode" :disabled="disabled" @input="emitChange" />
        </el-form-item>
        <el-form-item label="提示标签">
          <el-input v-model="selected.params.label" placeholder="科室代码" :disabled="disabled" @input="emitChange" />
        </el-form-item>
        <el-form-item label="校验正则" required>
          <el-input v-model="selected.params.pattern" placeholder="\d{2}" :disabled="disabled" @input="emitChange" />
        </el-form-item>
        <div class="params-grid">
          <el-form-item label="补零位数">
            <el-input-number v-model="selected.params.pad" :min="0" :max="18" :disabled="disabled" @change="emitChange" />
          </el-form-item>
          <el-form-item label="最大长度">
            <el-input-number v-model="selected.params.maxLength" :min="1" :max="255" :disabled="disabled" @change="emitChange" />
          </el-form-item>
          <el-form-item label="默认值">
            <el-input v-model="selected.params.default" :disabled="disabled" @input="emitChange" />
          </el-form-item>
          <el-form-item label="必填">
            <el-switch v-model="selected.params.required" :disabled="disabled" @change="emitChange" />
          </el-form-item>
        </div>
      </template>
    </div>

    <!-- 实时预览与校验 -->
    <div class="preview-bar">
      <span class="preview-label">实时预览</span>
      <div class="preview-samples">
        <code v-for="(s, i) in previewSamples" :key="i" class="preview-sample">{{ s }}</code>
        <span v-if="previewSamples.length === 0" class="preview-empty">（无法生成预览）</span>
      </div>
      <el-button
        size="small"
        plain
        :disabled="disabled || previewSamples.length === 0 || previewSamples.length >= 5"
        @click="generateAnother"
      >
        再生成一个
      </el-button>
      <el-tooltip v-if="formatRegex" content="派生的格式校验正则" placement="top">
        <code class="preview-regex">{{ formatRegex }}</code>
      </el-tooltip>
    </div>
    <div v-if="previewOffline" class="preview-offline-note">
      预览服务暂不可达，仅进行本地静态校验（不阻断保存）
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
import { ElMessageBox } from 'element-plus';
import { identifierStrategyApi, type StrategyPreset } from '@/api/identifierStrategy';

interface SegmentDef {
  type: string;
  name?: string;
  params: Record<string, any>;
}

const props = defineProps<{
  modelValue: string;
  /** 有命名空间时走按命名空间预览，否则走无命名空间预览端点 */
  namespaceId?: string;
  /** 禁用编辑（如初始化向导选择手动录入模式时） */
  disabled?: boolean;
}>();

const emit = defineEmits<{ 'update:modelValue': [value: string] }>();

/** 唯一性段：三者必须且只能择其一，保证标识唯一。 */
const UNIQUENESS_TYPES = ['SEQUENCE', 'TIMESTAMP', 'UUID'];

const paletteGroups = [
  {
    label: '内容段',
    items: [
      { type: 'LITERAL', label: '固定字符串' },
      { type: 'OBJECT_ABBREVIATION', label: '元数据对象缩写' },
      { type: 'VARIABLE', label: '输入变量' },
    ],
  },
  {
    label: '唯一性段（三者择一）',
    items: [
      { type: 'SEQUENCE', label: '顺序号' },
      { type: 'TIMESTAMP', label: '时间戳' },
      { type: 'UUID', label: 'UUID' },
    ],
  },
  {
    label: '修饰段',
    items: [
      { type: 'SEPARATOR', label: '分隔符' },
      { type: 'CHECKSUM', label: '校验位' },
    ],
  },
];

const palette = paletteGroups.flatMap((g) => g.items);
const separatorChars = ['.', '-', '_', '/', ':'];
const timestampPresets = [
  { label: '年月日（yyyyMMdd）', value: 'yyyyMMdd' },
  { label: '年月日时分秒（yyyyMMddHHmmss）', value: 'yyyyMMddHHmmss' },
  { label: '含毫秒（yyyyMMddHHmmssSSS）', value: 'yyyyMMddHHmmssSSS' },
  { label: '含微秒（yyyyMMddHHmmssSSSSSS）', value: 'yyyyMMddHHmmssSSSSSS' },
];

const segments = ref<SegmentDef[]>([]);
const selectedIndex = ref(-1);
const presets = ref<StrategyPreset[]>([]);
const presetKey = ref<string>();
const previewSamples = ref<string[]>([]);
const formatRegex = ref('');
const validationErrors = ref<string[]>([]);
const loadWarnings = ref<string[]>([]);
const previewOffline = ref(false);
const abbrRows = ref<{ type: string; abbr: string }[]>([]);

const selected = computed(() =>
  selectedIndex.value >= 0 && selectedIndex.value < segments.value.length
    ? segments.value[selectedIndex.value]
    : null,
);

const currentPresetDescription = computed(
  () => presets.value.find((p) => p.key === presetKey.value)?.description || '',
);

/** 只读模板表达式视图：{LITERAL:WN}.{OBJECT_ABBREVIATION}.{SEQUENCE(pad=5)} */
const templateExpression = computed(() =>
  segments.value
    .map((seg) => {
      switch (seg.type) {
        case 'LITERAL': return `{LITERAL:${seg.params.value || ''}}`;
        case 'OBJECT_ABBREVIATION': return '{OBJECT_ABBREVIATION}';
        case 'SEQUENCE': return `{SEQUENCE(pad=${seg.params.pad ?? 6})}`;
        case 'UUID': return seg.params.form === 'short' ? '{UUID(short)}' : '{UUID}';
        case 'SEPARATOR': return seg.params.char || '';
        case 'TIMESTAMP': return `{TIMESTAMP:${seg.params.pattern || ''}}`;
        case 'CHECKSUM': return `{CHECKSUM:${seg.params.algorithm || 'luhn'}}`;
        case 'VARIABLE': return `{VARIABLE:${seg.name || ''}}`;
        default: return `{${seg.type}}`;
      }
    })
    .join(''),
);

/** 时间戳作为唯一性段时的精度校验提示（面板内即时红字）。 */
const timestampPrecisionError = computed(() => {
  if (!selected.value || selected.value.type !== 'TIMESTAMP') return '';
  const uniqueness = segments.value.filter((s) => UNIQUENESS_TYPES.includes(s.type));
  if (uniqueness.length !== 1 || uniqueness[0] !== selected.value) return '';
  const pattern = selected.value.params.pattern || '';
  if (!/S{3,}/.test(pattern)) {
    return '时间戳作为唯一性段时，格式必须包含至少毫秒精度（连续 3 个 S），否则无法保证唯一性';
  }
  return '';
});

function isUniqueness(type: string): boolean {
  return UNIQUENESS_TYPES.includes(type);
}

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
    default: return '';
  }
}

function defaultSegment(type: string): SegmentDef {
  switch (type) {
    case 'LITERAL': return { type, params: { value: 'WN' } };
    case 'OBJECT_ABBREVIATION':
      return { type, name: 'objectAbbr', params: { default: '' } };
    case 'SEQUENCE':
      return { type, name: 'seq', params: { start: 1, increment: 1, pad: 5, overflow: 'REJECT' } };
    case 'UUID': return { type, params: { form: 'full' } };
    case 'SEPARATOR': return { type, params: { char: '.' } };
    case 'TIMESTAMP': return { type, name: 'ts', params: { pattern: 'yyyyMMddHHmmssSSS' } };
    case 'CHECKSUM': return { type, params: { algorithm: 'luhn' } };
    case 'VARIABLE':
      return {
        type,
        name: `var${segments.value.length + 1}`,
        params: { label: '', pattern: '\\w+', pad: 0, maxLength: 32, default: '', required: true },
      };
    default: return { type, params: {} };
  }
}

/** 唯一性段三者择一：已存在时再添加需确认替换。 */
async function addSegment(type: string) {
  if (isUniqueness(type)) {
    const existing = segments.value.findIndex((s) => isUniqueness(s.type));
    if (existing >= 0) {
      try {
        await ElMessageBox.confirm(
          `模板中已存在唯一性段（${typeLabel(segments.value[existing].type)}）。`
          + '顺序号、时间戳、UUID 必须三者择其一，是否替换现有唯一性段？',
          '替换唯一性段',
          { confirmButtonText: '替换', cancelButtonText: '取消', type: 'warning' },
        );
      } catch {
        return;
      }
      segments.value.splice(existing, 1);
      if (selectedIndex.value === existing) selectedIndex.value = -1;
      else if (selectedIndex.value > existing) selectedIndex.value -= 1;
    }
  }
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

/**
 * 载入模板 JSON 时归一化存量段：RANDOM 段已移除（丢弃并提示）；
 * OBJECT_ABBREVIATION 段的逐模板 mapping、SEQUENCE 段的显式 scopeRefs
 * 不再支持（剥离，引擎端忽略/拒绝）。
 */
function applyConfigJson(json: string) {
  loadWarnings.value = [];
  try {
    const parsed = JSON.parse(json);
    const raw: SegmentDef[] = (parsed.segments || []).map((s: any) => ({
      type: s.type,
      name: s.name,
      params: { ...(s.params || {}) },
    }));
    const kept: SegmentDef[] = [];
    for (const seg of raw) {
      if (seg.type === 'RANDOM') {
        loadWarnings.value.push('已移除的 RANDOM（随机）段不再支持，载入时已丢弃。');
        continue;
      }
      if (seg.type === 'OBJECT_ABBREVIATION' && seg.params.mapping) {
        delete seg.params.mapping;
        loadWarnings.value.push('对象缩写段改用系统级映射表，逐模板 mapping 配置已剥离。');
      }
      if (seg.type === 'SEQUENCE' && seg.params.scopeRefs) {
        delete seg.params.scopeRefs;
        loadWarnings.value.push('顺序号作用域改为自动推导，显式 scopeRefs 配置已剥离。');
      }
      kept.push(seg);
    }
    segments.value = kept;
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

/**
 * 端点不可达时的降级静态校验：唯一性段计数、时间戳精度、必填参数。
 * 只做校验不做渲染，不阻断保存。
 */
function clientValidate(): string[] {
  const errors: string[] = [];
  const uniqueness = segments.value.filter((s) => isUniqueness(s.type));
  if (uniqueness.length === 0) {
    errors.push('唯一性段缺失：顺序号、时间戳、UUID 必须三者择其一，以保证标识唯一。');
  } else if (uniqueness.length > 1) {
    errors.push('唯一性段只能有一个：顺序号、时间戳、UUID 三者择一。');
  }
  const ts = uniqueness.find((s) => s.type === 'TIMESTAMP');
  if (ts && !/S{3,}/.test(ts.params.pattern || '')) {
    errors.push('时间戳作为唯一性段时，格式必须包含至少毫秒精度（连续 3 个 S）。');
  }
  for (const seg of segments.value) {
    if (seg.type === 'LITERAL' && !seg.params.value) errors.push('固定字符串段缺少内容。');
    if (seg.type === 'SEPARATOR' && !seg.params.char) errors.push('分隔符段未选择字符。');
    if (seg.type === 'TIMESTAMP' && !seg.params.pattern) errors.push('时间戳段缺少格式。');
    if (seg.type === 'VARIABLE' && !seg.name) errors.push('输入变量段缺少变量名。');
  }
  return errors;
}

let previewTimer: ReturnType<typeof setTimeout> | undefined;
function schedulePreview() {
  if (previewTimer) clearTimeout(previewTimer);
  previewTimer = setTimeout(refreshPreview, 300);
}

async function requestPreviewSample(): Promise<string> {
  const configJson = JSON.stringify({ segments: segments.value });
  const res = props.namespaceId
    ? await identifierStrategyApi.preview(props.namespaceId, configJson, sampleVariables(), 'DataElement')
    : await identifierStrategyApi.previewAnonymous(configJson, sampleVariables(), 'DataElement');
  formatRegex.value = res.formatRegex || '';
  validationErrors.value = res.validationErrors || [];
  return res.sample || '';
}

/** 预览统一走后端端点（含无命名空间上下文）；不可达时降级本地静态校验。 */
async function refreshPreview() {
  if (segments.value.length === 0) {
    previewSamples.value = [];
    formatRegex.value = '';
    validationErrors.value = [];
    previewOffline.value = false;
    return;
  }
  try {
    const sample = await requestPreviewSample();
    previewSamples.value = sample ? [sample] : [];
    previewOffline.value = false;
  } catch {
    previewSamples.value = [];
    formatRegex.value = '';
    validationErrors.value = clientValidate();
    previewOffline.value = true;
  }
}

/**
 * "再生成一个"：重新走预览端点（UUID/时间戳取新值）；顺序号预览不产生
 * 序号分配，样例按起始值 + 步长 × 序号在本地递增展示。最多连续 5 个。
 */
async function generateAnother() {
  if (previewSamples.value.length >= 5 || previewSamples.value.length === 0) return;
  let next: string;
  try {
    next = await requestPreviewSample();
    previewOffline.value = false;
  } catch {
    previewOffline.value = true;
    validationErrors.value = clientValidate();
    return;
  }
  if (!next) return;
  const seqSeg = segments.value.find((s) => s.type === 'SEQUENCE');
  if (seqSeg) {
    const start = seqSeg.params.start ?? 1;
    const increment = seqSeg.params.increment ?? 1;
    const pad = seqSeg.params.pad ?? 6;
    const current = String(start).padStart(pad, '0');
    const bumped = String(start + increment * previewSamples.value.length).padStart(pad, '0');
    next = next.replace(current, bumped);
  }
  previewSamples.value.push(next);
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
  try {
    const mapping = await identifierStrategyApi.objectAbbreviations();
    abbrRows.value = Object.entries(mapping).map(([type, abbr]) => ({ type, abbr }));
  } catch {
    abbrRows.value = [];
  }
});
</script>

<style scoped>
.segment-designer {
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  padding: 12px;
}
.segment-designer.is-disabled {
  opacity: 0.75;
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
.expression-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  padding: 6px 10px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
}
.expression-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  flex-shrink: 0;
}
.expression-text {
  font-family: monospace;
  font-size: 13px;
  word-break: break-all;
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
.segment-chip.uniqueness {
  border-style: dashed;
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
.palette-group-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.palette-item {
  padding-left: 12px;
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
.abbr-block {
  width: 100%;
}
.abbr-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 6px;
}
.preview-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  border-top: 1px dashed var(--el-border-color);
  padding-top: 10px;
  flex-wrap: wrap;
}
.preview-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.preview-samples {
  display: inline-flex;
  gap: 8px;
  flex-wrap: wrap;
}
.preview-sample {
  font-size: 15px;
  font-weight: 600;
}
.preview-empty {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.preview-offline-note {
  font-size: 12px;
  color: var(--el-color-warning);
  margin-top: 6px;
}
.preview-regex {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}
</style>
