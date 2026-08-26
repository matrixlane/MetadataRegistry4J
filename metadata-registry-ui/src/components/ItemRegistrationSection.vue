<template>
  <div class="item-registration-section">
    <el-alert
      v-if="!raNamespaceExists"
      type="warning"
      :closable="false"
      style="margin-bottom: 12px;"
    >
      <template #title>系统中还没有基于注册机构的命名空间</template>
      <div>
        创建自己的数据标准前，请先创建基于注册机构的命名空间。仅导入或编目外部组织的数据标准（使用外部命名空间）不受影响。
      </div>
      <el-button type="warning" size="small" style="margin-top: 8px;" @click="goCreateNamespace">
        去创建命名空间
      </el-button>
    </el-alert>
    <el-form-item label="命名空间" prop="registration.namespaceId" :rules="namespaceRules">
      <el-select
        v-model="model.namespaceId"
        placeholder="选择命名空间"
        style="width: 100%;"
        clearable
        @change="handleNamespaceChange"
      >
        <el-option
          v-for="ns in namespaces"
          :key="ns.id"
          :label="ns.label || ns.identifier || ns.name || ns.id"
          :value="ns.id"
        />
      </el-select>
    </el-form-item>
    <el-form-item v-if="showIdentifierChoice && !isExternalNamespace" label="标识符生成方式">
      <el-radio-group v-model="identifierChoice">
        <el-radio-button value="auto">自动生成</el-radio-button>
        <el-radio-button value="manual">手动输入</el-radio-button>
      </el-radio-group>
    </el-form-item>
    <el-form-item label="标识符" prop="registration.identifier" :rules="identifierRules">
      <el-input
        v-model="model.identifier"
        :disabled="identifierDisabled"
        :placeholder="identifierPlaceholder"
      />
    </el-form-item>
    <!-- 段模板变量段：按已发布模板动态渲染 -->
    <template v-if="identifierAutoInEffect && templateVariables.length > 0">
      <el-form-item
        v-for="v in templateVariables"
        :key="v.name"
        :label="v.label || v.name"
        :required="v.required"
      >
        <el-input
          v-model="variableValues[v.name]"
          :placeholder="v.pattern ? `格式：${v.pattern}` : ''"
          clearable
        />
      </el-form-item>
    </template>
    <el-form-item v-if="showVersionChoice" label="版本生成方式">
      <el-radio-group v-model="versionChoice">
        <el-radio-button value="auto">自动生成</el-radio-button>
        <el-radio-button value="manual">手动输入</el-radio-button>
      </el-radio-group>
    </el-form-item>
    <el-form-item label="版本" prop="registration.version" :rules="versionRules">
      <el-input
        v-model="model.version"
        :disabled="versionDisabled"
        :placeholder="versionPlaceholder"
      />
    </el-form-item>
    <el-form-item label="注册机构">
      <el-tag v-if="ownershipLabel" type="primary" effect="plain">{{ ownershipLabel }}</el-tag>
      <el-text v-else-if="activeRegistrarQueryFailed" type="danger">活动 Registrar 身份查询失败，请刷新页面重试或重新登录</el-text>
      <el-text v-else type="warning">当前账号无活动 Registrar 身份，无法注册条目</el-text>
      <el-text size="small" type="info" style="margin-left: 8px;">由活动 Registrar 身份决定</el-text>
    </el-form-item>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { identifierStrategyApi } from '@/api/identifierStrategy';
import { activeRegistrarApi, namespaceApi, readinessApi } from '@/api/resources';

export interface RegistrationModel {
  namespaceId?: string;
  identifier?: string;
  version?: string;
}

interface NamespaceOption {
  id: string;
  identifier?: string;
  name?: string;
  label?: string;
  identifierInputMode?: string;
  versionInputMode?: string;
  versioningPolicy?: string;
  manualIdentifierPattern?: string;
  namespaceKind?: string;
  schemeReference?: string;
}

interface TemplateVariable {
  name: string;
  label?: string;
  pattern?: string;
  pad?: number;
  default?: string;
  required?: boolean;
}

interface Props {
  /** 编辑态时为 true：标识符禁用、版本可编辑 */
  isEditing?: boolean;
  /** 编辑既有条目时其后端归属注册机构名（优先于活动 Registrar 显示） */
  registrationAuthorityName?: string | null;
}

const props = withDefaults(defineProps<Props>(), {
  isEditing: false,
  registrationAuthorityName: null,
});

/** 宿主表单中的 registration 对象，直接双向共享（宿主 el-form 负责 prop 路径校验） */
const model = defineModel<RegistrationModel>({ required: true });

const router = useRouter();

onMounted(loadOptions);

const namespaces = ref<NamespaceOption[]>([]);
const loaded = ref(false);
const raNamespaceExists = ref(true);
const activeRaName = ref<string | null>(null);
/** 活动 Registrar 身份查询本身失败（接口异常），与"无活动身份"区分提示 */
const activeRegistrarQueryFailed = ref(false);

/** system-default 命名空间的 URN 方案引用（与后端 SystemDefaultNamespaceInitializer 一致） */
const SYSTEM_DEFAULT_SCHEME = 'urn:metadata-registry:system-default';

const identifierChoice = ref<'auto' | 'manual'>('auto');
const versionChoice = ref<'auto' | 'manual'>('auto');
const templateVariables = ref<TemplateVariable[]>([]);
const variableValues = reactive<Record<string, string>>({});

const ownershipLabel = computed(() => props.registrationAuthorityName || activeRaName.value);

const selectedNamespace = computed(() =>
  namespaces.value.find((ns) => ns.id === model.value.namespaceId)
);

const identifierMode = computed(() => selectedNamespace.value?.identifierInputMode || 'AUTO_WITH_OVERRIDE');
const versionMode = computed(() => selectedNamespace.value?.versionInputMode || 'AUTO_WITH_OVERRIDE');

const showIdentifierChoice = computed(() => !props.isEditing && identifierMode.value === 'AUTO_WITH_OVERRIDE');
const showVersionChoice = computed(() => !props.isEditing && versionMode.value === 'AUTO_WITH_OVERRIDE');

// 外部命名空间：标识由外部签发，强制手动录入，无自动生成入口
const isExternalNamespace = computed(() => selectedNamespace.value?.namespaceKind === 'EXTERNAL');

const identifierAutoInEffect = computed(
  () => !props.isEditing && !isExternalNamespace.value
    && (identifierMode.value === 'AUTO_ONLY' || (identifierMode.value === 'AUTO_WITH_OVERRIDE' && identifierChoice.value === 'auto')),
);
const versionAutoInEffect = computed(
  () => !props.isEditing && (versionMode.value === 'AUTO_ONLY' || (versionMode.value === 'AUTO_WITH_OVERRIDE' && versionChoice.value === 'auto')),
);

const identifierDisabled = computed(() => props.isEditing || identifierAutoInEffect.value);
// 版本在编辑态可修改：显式指定则采用，留空保存按命名空间版本策略递增
const versionDisabled = computed(() => versionAutoInEffect.value);

const identifierPlaceholder = computed(() => {
  if (props.isEditing) return '';
  return identifierAutoInEffect.value ? '保存后自动生成' : '唯一标识符';
});

const versionPlaceholder = computed(() => {
  if (props.isEditing) return '留空则按命名空间策略自动递增';
  if (versionAutoInEffect.value) return '保存后自动生成';
  switch (selectedNamespace.value?.versioningPolicy) {
    case 'SEQUENTIAL': return '如 1';
    case 'DATE': return '如 2026-01-31';
    default: return '如 1.0.0';
  }
});

const namespaceRules = [
  { required: true, message: '请选择命名空间', trigger: 'change' },
];

const identifierRules = [
  {
    validator: (_rule: unknown, value: string, callback: (e?: Error) => void) => {
      if (props.isEditing || identifierAutoInEffect.value) {
        callback();
        return;
      }
      if (!value || !value.trim()) {
        callback(new Error('请输入标识符'));
        return;
      }
      const pattern = selectedNamespace.value?.manualIdentifierPattern;
      if (pattern) {
        try {
          if (!new RegExp(pattern).test(value.trim())) {
            callback(new Error(`标识符不符合校验规则: ${pattern}`));
            return;
          }
        } catch {
          // 命名空间配置的正则非法时交给后端处理
        }
      }
      callback();
    },
    trigger: 'blur',
  },
];

const versionRules = [
  {
    validator: (_rule: unknown, value: string, callback: (e?: Error) => void) => {
      if (props.isEditing || versionAutoInEffect.value) {
        callback();
        return;
      }
      if (versionMode.value === 'MANUAL_ONLY' && (!value || !value.trim())) {
        callback(new Error('该命名空间已禁用自动生成，版本号必填'));
        return;
      }
      callback();
    },
    trigger: 'blur',
  },
];

// 无注册机构关联命名空间时：仅当目标为 system-default 命名空间才禁用保存
// （外部命名空间用于导入/编目外部数据标准，不受门槛限制）。
const gateBlocked = computed(() =>
  !raNamespaceExists.value && selectedNamespace.value?.schemeReference === SYSTEM_DEFAULT_SCHEME
);

function goCreateNamespace() {
  router.push('/namespaces');
}

function handleNamespaceChange() {
  identifierChoice.value = 'auto';
  versionChoice.value = 'auto';
  model.value.identifier = undefined;
  model.value.version = undefined;
}

async function loadTemplateVariables(namespaceId: string) {
  templateVariables.value = [];
  Object.keys(variableValues).forEach((k) => delete variableValues[k]);
  try {
    const view = await identifierStrategyApi.view(namespaceId);
    const published = view?.published;
    if (!published || published.strategyType !== 'SEGMENT_TEMPLATE' || !published.configJson) {
      return;
    }
    const parsed = JSON.parse(published.configJson);
    const vars: TemplateVariable[] = (parsed.segments || [])
      .filter((s: any) => s.type === 'VARIABLE' && s.name)
      .map((s: any) => ({
        name: s.name,
        label: s.params?.label,
        pattern: s.params?.pattern,
        pad: s.params?.pad,
        default: s.params?.default,
        required: s.params?.required !== false,
      }));
    templateVariables.value = vars;
    for (const v of vars) {
      if (v.default) {
        variableValues[v.name] = v.default;
      }
    }
  } catch {
    templateVariables.value = [];
  }
}

watch(() => model.value.namespaceId, (namespaceId) => {
  if (namespaceId) {
    loadTemplateVariables(namespaceId);
  } else {
    templateVariables.value = [];
    Object.keys(variableValues).forEach((k) => delete variableValues[k]);
  }
});

async function loadOptions() {
  if (loaded.value) return;
  try {
    const nsRes = await namespaceApi.list({ size: 100 });
    namespaces.value = (nsRes.content || []) as any;
    loaded.value = true;
  } catch {
    // ignore
  }
  // 活动 Registrar 身份决定条目归属（active-registrar-ownership）
  try {
    const ar = await activeRegistrarApi.current();
    activeRaName.value = ar.active?.registrationAuthorityName || null;
  } catch {
    activeRaName.value = null;
    activeRegistrarQueryFailed.value = true;
  }
  // 命名空间前置门槛查询失败时默认放行（后端仍会强制校验），不阻塞对话框使用。
  try {
    const gate = await readinessApi.raNamespaceExists();
    raNamespaceExists.value = gate.exists;
  } catch {
    raNamespaceExists.value = true;
  }
}

/** 提交前校验变量段：必填非空 + 段级 pattern。返回 null 表示通过。 */
function validateTemplateVariables(): string | null {
  for (const v of templateVariables.value) {
    const value = (variableValues[v.name] || '').trim();
    if (!value) {
      if (v.required) {
        return `请填写${v.label || v.name}`;
      }
      continue;
    }
    if (v.pattern) {
      try {
        if (!new RegExp(`^(?:${v.pattern})$`).test(value)) {
          return `${v.label || v.name} 不符合格式要求：${v.pattern}`;
        }
      } catch {
        // 段正则非法交给后端处理
      }
    }
  }
  return null;
}

/** 组装提交负载中的 registration 部分。 */
function buildPayload() {
  return {
    namespaceId: model.value.namespaceId || null,
    identifier: identifierAutoInEffect.value ? null : model.value.identifier || '',
    version: versionAutoInEffect.value ? null : model.value.version || '',
    variables: identifierAutoInEffect.value && templateVariables.value.length > 0
      ? { ...variableValues }
      : undefined,
  };
}

function reset() {
  identifierChoice.value = 'auto';
  versionChoice.value = 'auto';
  templateVariables.value = [];
  Object.keys(variableValues).forEach((k) => delete variableValues[k]);
}

defineExpose({
  loadOptions,
  reset,
  buildPayload,
  validateTemplateVariables,
  gateBlocked,
  identifierAutoInEffect,
  versionAutoInEffect,
});
</script>
