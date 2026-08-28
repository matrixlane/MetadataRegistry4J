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
    <el-form-item label="标识符" prop="registration.identifier" :rules="identifierRules">
      <el-input
        v-model="model.identifier"
        :disabled="identifierDisabled"
        :placeholder="identifierPlaceholder"
      >
        <template v-if="showIdentifierChoice && !isExternalNamespace" #append>
          <el-segmented
            v-model="identifierChoice"
            :options="generationModeOptions"
            size="small"
            role="radiogroup"
            aria-label="标识符生成方式"
          />
        </template>
      </el-input>
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
    <el-form-item label="版本" prop="registration.version" :rules="versionRules">
      <el-input
        v-model="model.version"
        :disabled="versionDisabled"
        :placeholder="versionPlaceholder"
      >
        <template v-if="showVersionChoice" #append>
          <el-segmented
            v-model="versionChoice"
            :options="generationModeOptions"
            size="small"
            role="radiogroup"
            aria-label="版本生成方式"
          />
        </template>
      </el-input>
    </el-form-item>
    <el-form-item label="注册机构">
      <el-tag v-if="ownershipLabel" type="primary" effect="plain">{{ ownershipLabel }}</el-tag>
      <el-text v-else-if="activeRegistrarQueryFailed" type="danger">活动 Registrar 身份查询失败，请刷新页面重试或重新登录</el-text>
    </el-form-item>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { onBeforeUnmount } from 'vue';
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
  /** 关联的 Registration_Authority id，用于按活动 Registrar 匹配默认命名空间 */
  registrationAuthorityId?: string;
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
const activeRaRegistrationAuthorityId = ref<string | null>(null);
/** 活动 Registrar 身份查询本身失败（接口异常），与"无活动身份"区分提示 */
const activeRegistrarQueryFailed = ref(false);

/** system-default 命名空间的 URN 方案引用（与后端 SystemDefaultNamespaceInitializer 一致） */
const SYSTEM_DEFAULT_SCHEME = 'urn:metadata-registry:system-default';

const identifierChoice = ref<'auto' | 'manual'>('auto');
const versionChoice = ref<'auto' | 'manual'>('auto');
const generationModeOptions = [
  { label: '自动', value: 'auto' },
  { label: '手动', value: 'manual' },
];
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
    required: true,
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
    required: true,
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

/** 命名空间级初始版本号预测：与后端 IdentifierInputPolicyService.initialVersion 同步，避免 manual 模式下空串被静默填充为该默认值。 */
function predictInitialVersion(policy?: string): string {
  switch (policy) {
    case 'SEQUENTIAL':
      return '1';
    case 'DATE':
      return new Date().toISOString().slice(0, 10);
    default:
      return '1.0.0';
  }
}

/** 模式切换事件：通知宿主 dialog 在切回自动时清除对应字段的旧校验错误，
 *  并在切到手动且 AUTO_WITH_OVERRIDE 时预填版本预测值，让用户即时看到/可改默认版本。 */
const emit = defineEmits<{
  'mode-change': [{ field: 'identifier' | 'version'; to: 'auto' | 'manual' }];
}>();

watch(
  [identifierChoice, versionChoice],
  ([newId, newVer], [oldId, oldVer]) => {
    if (newId !== oldId) {
      emit('mode-change', { field: 'identifier', to: newId });
    }
    if (newVer !== oldVer) {
      emit('mode-change', { field: 'version', to: newVer });
      // AUTO_WITH_OVERRIDE + 手动：预填预测的初始版本，避免静默落 1.0.0
      if (
        newVer === 'manual'
        && oldVer === 'auto'
        && versionMode.value === 'AUTO_WITH_OVERRIDE'
        && !model.value.version
      ) {
        model.value.version = predictInitialVersion(selectedNamespace.value?.versioningPolicy);
      }
    }
  },
);

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
  } catch {
    // ignore
  }
  // 活动 Registrar 身份决定条目归属（active-registrar-ownership）
  try {
    const ar = await activeRegistrarApi.current();
    activeRaName.value = ar.active?.registrationAuthorityName || null;
    activeRaRegistrationAuthorityId.value = ar.active?.registrationAuthorityId || null;
  } catch {
    activeRaName.value = null;
    activeRaRegistrationAuthorityId.value = null;
    activeRegistrarQueryFailed.value = true;
  }
  // 命名空间前置门槛查询失败时默认放行（后端仍会强制校验），不阻塞对话框使用。
  try {
    const gate = await readinessApi.raNamespaceExists();
    raNamespaceExists.value = gate.exists;
  } catch {
    raNamespaceExists.value = true;
  }
  // 默认命名空间预选（default-namespace-by-active-registrar）。置于最末，
  // 仅当 active RA / namespaces / readiness 三路全部就位后再写入 model。
  // 此后才上报 loaded=true（外层以 gateBlocked 可读作为 loadOptions 完成的信号），
  // 避免宿主/测试在默认推导前写入并被异步覆盖。
  applyDefaultNamespace();
  loaded.value = true;
}

/**
 * 活动 Registrar 身份变更时重新应用默认命名空间（一切上下文随之切换）。
 * 仅创建态生效；活动身份未改变时不覆盖用户已显式选择的命名空间。
 */
function applyDefaultNamespace() {
  if (props.isEditing) return;
  // 无活动身份：不预选；保留用户此前可能的选择（新建场景下通常未选）
  const targetRaId = activeRaRegistrationAuthorityId.value;
  if (!targetRaId) return;
  // 不覆盖用户的显式选择；若用户已选定了同名同 RA 的命名空间则视为一致
  // 用户已选定任意命名空间：不覆写。处理顺序在这里很关键——
  // loadOptions 尾部与 watcher 重推导先后发生，需保证用户选择不被覆盖。
  const current = namespaces.value.find((ns) => ns.id === model.value.namespaceId);
  if (current) {
    return;
  }
  // 自有命名空间 = 注册机构关联 + 非外部 + 非 system-default
  const own = namespaces.value.find(
    (ns) =>
      ns.registrationAuthorityId === targetRaId
      && ns.namespaceKind !== 'EXTERNAL'
      && ns.schemeReference !== SYSTEM_DEFAULT_SCHEME,
  );
  if (own) {
    model.value.namespaceId = own.id;
  }
}

// 监听会话级活动 Registrar 变化（顶栏切换器广播的事件，default-namespace-by-active-registrar）。
// 接收事件后重新拉取活动身份并重应用默认；不变更用户已编辑的标识/版本值。
function handleActiveRegistrarChanged() {
  if (props.isEditing) return;
  void (async () => {
    try {
      const ar = await activeRegistrarApi.current();
      activeRaName.value = ar.active?.registrationAuthorityName || null;
      activeRaRegistrationAuthorityId.value = ar.active?.registrationAuthorityId || null;
      activeRegistrarQueryFailed.value = false;
      // 身份切换后清理用户已选的命名空间，重新推导默认
      model.value.namespaceId = undefined;
      applyDefaultNamespace();
    } catch {
      activeRegistrarQueryFailed.value = true;
    }
  })();
}

onMounted(() => {
  window.addEventListener('mdregistry:active-registrar-changed', handleActiveRegistrarChanged);
});
onBeforeUnmount(() => {
  window.removeEventListener('mdregistry:active-registrar-changed', handleActiveRegistrarChanged);
});

// 兜底：本组件内部 activeRaRegistrationAuthorityId 变化时也重应用默认（不由顶栏事件触发也会经过此 watcher）。
// 监听活动 Registrar 变化后重应用默认。loaded 守卫：初始 loadOptions 中 activeAuid
// 首次赋值不该覆盖用户已选的命名空间；只有 loaded=true 之后发生的身份变更才清空重选。
watch(activeRaRegistrationAuthorityId, () => {
  if (props.isEditing) return;
  if (!loaded.value) return;
  model.value.namespaceId = undefined;
  applyDefaultNamespace();
});

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
  applyDefaultNamespace,
  refreshActiveRegistrar: async () => {
    // 供测试与上层宿主调用：主动重拉活动 RA 并重应用默认。
    await handleActiveRegistrarChanged();
  },
  reset,
  buildPayload,
  validateTemplateVariables,
  gateBlocked,
  identifierAutoInEffect,
  versionAutoInEffect,
});
</script>
