<template>
  <div class="login-layout">
    <el-card class="wizard-card" shadow="always">
      <div class="wizard-header">
        <img src="/favicon.svg" width="56" height="56" alt="logo" />
        <h2>初始化配置向导</h2>
        <p>系统首次部署完成，请按照以下步骤完成基础配置</p>
      </div>

      <el-alert
        v-if="currentStep === 5"
        type="warning"
        :closable="false"
        show-icon
        class="final-step-alert"
      >
        <template #title>
          <strong>这是初始化的最后一步。</strong>
          完成管理员密码设置后系统将正式启用。中途中断将清除已配置的所有数据，请谨慎操作。
        </template>
      </el-alert>

      <el-steps :active="displayActive" align-center class="mb-8" :class="{ 'final-step-active': currentStep === 5 }">
        <el-step
          v-for="title in displaySteps"
          :key="title"
          :title="title"
          :class="{ 'final-step': title === '管理员密码' }"
        />
      </el-steps>

      <div class="step-content">
        <!-- Step 1: Deployment Edition Selection -->
        <div v-if="currentStep === 0" class="step-pane fade-in">
          <h3 class="step-title">选择部署版本</h3>
          <p class="step-desc">请选择本系统的部署形态：个人单机使用，或团队局域网协作</p>

          <div class="mode-options">
            <div
              class="mode-card"
              :class="{ active: form.edition === 'PERSONAL' }"
              @click="form.edition = 'PERSONAL'"
            >
              <div class="mode-icon-wrapper">
                <el-icon :size="32" class="mode-icon"><Setting /></el-icon>
              </div>
              <div class="mode-info">
                <h4>个人版 (默认)</h4>
                <p>单机部署，开箱即用。默认使用内置 SQLite 数据库，也支持配置独立数据库。适合个人或小团队独立开展数据治理。</p>
              </div>
              <div v-if="form.edition === 'PERSONAL'" class="mode-badge-active">
                <el-icon><Check /></el-icon>已选择
              </div>
            </div>

            <div
              class="mode-card"
              :class="{ active: form.edition === 'TEAM' }"
              @click="form.edition = 'TEAM'"
            >
              <div class="mode-icon-wrapper">
                <el-icon :size="32" class="mode-icon"><OfficeBuilding /></el-icon>
              </div>
              <div class="mode-info">
                <h4>团队版</h4>
                <p>局域网部署，多部门协作。需配置独立数据库（SQL Server / 达梦），提供完整的注册机构治理配置，并支持多注册机构策略。</p>
              </div>
              <div v-if="form.edition === 'TEAM'" class="mode-badge-active">
                <el-icon><Check /></el-icon>已选择
              </div>
            </div>
          </div>
        </div>

        <!-- Step 2: Datasource Configuration -->
        <div v-if="currentStep === 1" class="step-pane fade-in">
          <h3 class="step-title">配置数据库</h3>
          <p class="step-desc" v-if="form.edition === 'PERSONAL'">
            个人版默认使用内置 SQLite 数据库，可直接继续使用；也可以配置独立数据库（SQL Server / 达梦）。
          </p>
          <p class="step-desc" v-else>
            团队版需要配置独立数据库（SQL Server / 达梦），不支持内置 SQLite。
          </p>

          <el-card v-if="currentDb" class="db-current-card" shadow="never">
            <div class="db-current-title">当前数据源</div>
            <div class="db-current-line">方言：{{ currentDb.vendor }}</div>
            <div class="db-current-line">地址：{{ currentDb.jdbcUrl || '-' }}</div>
          </el-card>

          <template v-if="!restartRequired">
            <el-radio-group v-model="dbChoice" class="db-choice">
              <el-radio value="keep">保持当前数据源并继续</el-radio>
              <el-radio value="change">配置新的数据源</el-radio>
            </el-radio-group>

            <el-form v-if="dbChoice === 'change'" label-position="top" class="wizard-form db-form">
              <el-form-item label="数据库类型" required>
                <el-select v-model="dbForm.vendor" style="width: 100%">
                  <el-option v-if="form.edition === 'PERSONAL'" label="SQLite（内置文件库）" value="sqlite" />
                  <el-option label="SQL Server" value="sqlserver" />
                  <el-option label="达梦 DM8" value="dm" />
                </el-select>
              </el-form-item>

              <el-form-item v-if="dbForm.vendor === 'sqlite'" label="数据库文件路径" required>
                <el-input v-model="dbForm.filePath" placeholder="./data/metadata-registry.db" />
              </el-form-item>

              <template v-else>
                <el-form-item label="主机" required>
                  <el-input v-model="dbForm.host" placeholder="localhost" />
                </el-form-item>
                <el-form-item label="端口">
                  <el-input-number v-model="dbForm.port" :min="1" :max="65535" style="width: 100%" />
                </el-form-item>
                <el-form-item label="数据库名" required>
                  <el-input v-model="dbForm.database" placeholder="metadata_registry" />
                </el-form-item>
                <el-form-item label="用户名（留空表示 Windows 集成认证/默认）">
                  <el-input v-model="dbForm.username" autocomplete="off" />
                </el-form-item>
                <el-form-item label="密码">
                  <el-input v-model="dbForm.password" type="password" show-password autocomplete="new-password" />
                </el-form-item>
              </template>

              <el-form-item>
                <el-button :loading="dbTesting" @click="testDbConnection">测试连接</el-button>
                <span v-if="dbTestResult" class="db-test-result" :class="{ ok: dbTestResult.success }">
                  {{ dbTestResult.message }}
                </span>
              </el-form-item>
            </el-form>
          </template>

          <el-alert
            v-if="restartRequired"
            type="success"
            :closable="false"
            show-icon
            title="数据源配置已保存"
            description="新配置已写入 application-local.yml，将在重启后生效。请重启应用，然后重新进入本向导继续初始化。"
          />
        </div>

        <!-- Step 3: Registration Authority Details -->
        <div v-if="currentStep === 2" class="step-pane fade-in">
          <h3 class="step-title">配置默认注册机构</h3>
          <p class="step-desc">注册机构是根据标准开展元数据管理和数据治理的最高权力组织机构</p>

          <el-form
            ref="formStep2Ref"
            :model="form"
            :rules="rulesStep2"
            label-position="top"
            class="wizard-form"
          >
            <el-form-item label="注册机构名称" prop="registrationAuthorityName">
              <el-input
                v-model="form.registrationAuthorityName"
                placeholder="例如：信息化管理部、数据治理委员会"
                size="large"
                clearable
              >
                <template #prefix>
                  <el-icon><OfficeBuilding /></el-icon>
                </template>
              </el-input>
            </el-form-item>

            <el-form-item label="注册机构英文名称" prop="registrationAuthorityEnglishName">
              <el-input
                v-model="form.registrationAuthorityEnglishName"
                placeholder="例如：Information Management Department"
                size="large"
                clearable
              >
                <template #prefix>
                  <el-icon><OfficeBuilding /></el-icon>
                </template>
              </el-input>
            </el-form-item>

            <el-form-item label="联系邮箱" prop="registrationAuthorityEmail">
              <el-input
                v-model="form.registrationAuthorityEmail"
                placeholder="例如：ra-admin@example.com"
                size="large"
                clearable
              >
                <template #prefix>
                  <el-icon><Message /></el-icon>
                </template>
              </el-input>
            </el-form-item>
          </el-form>

          <div class="wizard-settings-box">
            <div class="setting-item">
              <div class="setting-label-section">
                <h4>将当前超级管理员设为该机构的注册管理员</h4>
                <p>勾选后，当前登录的超级管理员将直接兼任该注册机构的注册管理员，从而拥有在该机构下创建命名空间、导入联系人的权限。</p>
              </div>
              <el-switch v-model="form.selfAsRegistrar" size="large" />
            </div>
          </div>
        </div>

        <!-- Step 4: Namespace creation -->
        <div v-if="currentStep === 3" class="step-pane fade-in">
          <h3 class="step-title">配置命名空间</h3>
          <p class="step-desc">命名空间是创建自有数据标准的前提；标识与版本生成策略均在命名空间级别生效</p>

          <div class="wizard-settings-box">
            <div class="setting-item">
              <div class="setting-label-section">
                <h4>创建基于注册机构的命名空间</h4>
                <p>系统将在注册机构下创建一个自有命名空间，作为注册自有元数据项和数据标准的起步区域。初始化必须创建自有命名空间，此步骤不可跳过。</p>
              </div>
              <el-tag type="success" effect="dark" size="large">必选</el-tag>
            </div>
          </div>

          <el-form
            v-if="form.createNamespace"
            ref="formStep3Ref"
            :model="form"
            :rules="rulesStep3"
            label-position="top"
            class="wizard-form"
          >
            <el-form-item label="命名空间名称" prop="namespaceName">
              <el-input
                v-model="form.namespaceName"
                placeholder="例如：默认命名空间"
                size="large"
                clearable
              />
            </el-form-item>

            <el-divider content-position="left">标识生成策略</el-divider>
            <el-form-item label="输入模式" prop="identifierInputMode">
              <el-radio-group v-model="form.identifierInputMode">
                <el-radio-button value="AUTO_ONLY">自动生成</el-radio-button>
                <el-radio-button value="AUTO_WITH_OVERRIDE">自动生成（允许手动编辑）</el-radio-button>
                <el-radio-button value="MANUAL_ONLY">手动输入</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-alert
              v-if="form.identifierInputMode === 'MANUAL_ONLY'"
              title="手动模式适用于外部标准命名空间：创建时必须显式填写标识符，系统不做自动生成。"
              type="info"
              :closable="false"
              show-icon
              style="margin-bottom: 12px;"
            />
            <el-form-item label="标识模板" prop="identifierTemplate">
              <SegmentTemplateDesigner
                v-model="form.identifierTemplate"
                :disabled="form.identifierInputMode === 'MANUAL_ONLY'"
                style="width: 100%;"
              />
            </el-form-item>

            <el-divider content-position="left">版本生成策略</el-divider>
            <el-form-item label="输入模式" prop="versionInputMode">
              <el-radio-group v-model="form.versionInputMode">
                <el-radio-button value="AUTO_ONLY">自动生成</el-radio-button>
                <el-radio-button value="AUTO_WITH_OVERRIDE">自动生成（允许手动编辑）</el-radio-button>
                <el-radio-button value="MANUAL_ONLY">手动输入</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-alert
              v-if="form.versionInputMode === 'MANUAL_ONLY'"
              title="纯手动模式：创建时必须显式填写版本号，系统不做自动生成。"
              type="info"
              :closable="false"
              show-icon
              style="margin-bottom: 12px;"
            />
            <el-form-item label="版本生成策略" prop="versionPolicy">
              <el-select v-model="form.versionPolicy" size="large" placeholder="请选择版本生成策略" style="width: 100%;" :disabled="form.versionInputMode === 'MANUAL_ONLY'">
                <el-option label="语义化版本 (X.Y.Z)" value="SEMVER" />
                <el-option label="顺序整数 (1, 2, 3, ...)" value="SEQUENTIAL" />
                <el-option label="日期版本 (YYYY-MM-DD)" value="DATE" />
              </el-select>
            </el-form-item>
          </el-form>
        </div>

        <!-- Step 5: Registry Specification Configuration -->
        <div v-if="currentStep === 4" class="step-pane fade-in">
          <h3 class="step-title">配置注册规范</h3>
          <p class="step-desc">注册规范决定了元数据在注册表中的管理与校验规则，是开展数据治理工作的先决条件</p>

          <div class="wizard-settings-box mb-4">
            <div class="setting-item">
              <div class="setting-label-section">
                <h4>立即创建并启用注册规范</h4>
                <p>注册规范是开展数据治理工作的先决条件，初始化时必须创建。只有创建了注册规范后，本注册表才正式允许数据治理工作的开展和注册项目。</p>
              </div>
              <el-switch v-model="form.createRegistrySpecification" size="large" disabled />
            </div>
          </div>

          <el-form
            v-if="form.createRegistrySpecification"
            ref="formStep4Ref"
            :model="form"
            :rules="rulesStep4"
            label-position="top"
            class="wizard-form"
          >
            <el-form-item label="注册规范（注册表）名称" prop="registryName">
              <el-input
                v-model="form.registryName"
                placeholder="例如：主元数据注册表规范"
                size="large"
                clearable
              />
            </el-form-item>

            <el-form-item label="默认语言" prop="defaultLanguage">
              <el-select v-model="form.defaultLanguage" size="large" placeholder="请选择默认语言" style="width: 100%;">
                <el-option label="简体中文 (zh-CN)" value="zh-CN" />
                <el-option label="英文 (en-US)" value="en-US" />
              </el-select>
            </el-form-item>

            <el-form-item label="描述" prop="registryDescription">
              <el-input
                v-model="form.registryDescription"
                type="textarea"
                :rows="2"
                placeholder="请输入关于此注册规范的简要描述"
              />
            </el-form-item>
          </el-form>
        </div>

        <!-- Step 5: Set Admin Password — final commit step -->
        <div v-if="currentStep === 5" class="step-pane fade-in">
          <h3 class="step-title">设置超级管理员密码</h3>
          <p class="step-desc">
            这是初始化的最后一步。提交后系统将正式启用，请妥善保管新密码。
          </p>

          <el-alert type="info" :closable="false" show-icon class="mb-4">
            <template #title>
              <strong>当前管理员账户：</strong>admin
              <br />
              <span style="font-size: 13px;">
                系统初始化期间管理员密码处于临时状态（无需输入原密码）。
                提交后，临时状态将被清除，admin 必须使用新密码登录。
              </span>
            </template>
          </el-alert>

          <el-alert
            v-if="commitError"
            type="error"
            :title="commitError"
            show-icon
            :closable="false"
            class="mb-4"
          />

          <el-form
            ref="formStep5Ref"
            :model="pwdForm"
            :rules="rulesStep5"
            label-position="top"
            class="wizard-form"
          >
            <el-form-item label="新密码" prop="newPassword">
              <el-input
                v-model="pwdForm.newPassword"
                type="password"
                name="newPassword"
                autocomplete="new-password"
                placeholder="6-20 个字符"
                size="large"
                show-password
              />
            </el-form-item>

            <el-form-item label="确认新密码" prop="confirmPassword">
              <el-input
                v-model="pwdForm.confirmPassword"
                type="password"
                name="confirmPassword"
                autocomplete="new-password"
                placeholder="再次输入新密码"
                size="large"
                show-password
              />
            </el-form-item>
          </el-form>
        </div>
      </div>

      <div class="wizard-footer">
        <el-button
          v-if="currentStep > 0"
          size="large"
          style="width: 120px;"
          :disabled="loading"
          @click="prevStep"
        >
          上一步
        </el-button>
        <el-button
          v-if="currentStep < lastStepIndex && !restartRequired"
          type="primary"
          size="large"
          style="width: 120px; margin-left: auto;"
          :loading="loading"
          @click="nextStep"
        >
         下一步
        </el-button>
        <el-button
          v-else-if="!restartRequired"
          type="success"
          size="large"
          :loading="loading"
          style="width: 200px; margin-left: auto;"
          @click="handleCommit"
        >
          完成初始化并启用系统
        </el-button>
        <el-button
          v-else
          size="large"
          style="width: 200px; margin-left: auto;"
          disabled
        >
          请重启应用后继续
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount } from 'vue';
import { useRouter, onBeforeRouteLeave } from 'vue-router';
import type { FormInstance, FormRules } from 'element-plus';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Setting, OfficeBuilding, Check, Message } from '@element-plus/icons-vue';
import request from '@/utils/request';
import { useAuthStore } from '@/stores/auth';
import SegmentTemplateDesigner from '@/components/identifier/SegmentTemplateDesigner.vue';

const router = useRouter();
const auth = useAuthStore();

const currentStep = ref(0);
const loading = ref(false);
const setupCommitted = ref(false);
const commitError = ref('');

// ── Step 2 (database) state ─────────────────────────────────────
interface CurrentDb {
  vendor: string;
  jdbcUrl: string | null;
  schema: string | null;
  driverClassName: string | null;
}
interface DbTestResult {
  success: boolean;
  message: string;
  databaseProductName?: string;
  databaseVersion?: string;
}
const currentDb = ref<CurrentDb | null>(null);
const dbChoice = ref<'keep' | 'change'>('keep');
const dbForm = reactive({
  vendor: 'sqlserver',
  host: 'localhost',
  port: 1433,
  database: 'metadata_registry',
  filePath: './data/metadata-registry.db',
  username: '',
  password: '',
});
const dbTesting = ref(false);
const dbSaving = ref(false);
const dbTestResult = ref<DbTestResult | null>(null);
const restartRequired = ref(false);

// SQLite 模式下凭据输入框只是隐藏不渲染，残留值（如浏览器自动填充）仍会随保存请求提交
watch(() => dbForm.vendor, () => {
  dbForm.username = '';
  dbForm.password = '';
  dbTestResult.value = null;
});

// 连接参数变化后，之前的测试结果即失效——"下一步"保存必须以最新参数的测试结果为准
watch(
  () => [dbForm.host, dbForm.port, dbForm.database, dbForm.filePath, dbForm.username, dbForm.password],
  () => { dbTestResult.value = null; }
);

// 切到"配置新的数据源"时对表单打快照；"下一步"时若表单与快照一致
// （没有做过任何修改），等价于"保持当前数据源并继续"，不保存也不重启
const dbFormSnapshot = ref('');
watch(dbChoice, (choice) => {
  if (choice === 'change') {
    dbFormSnapshot.value = JSON.stringify(dbForm);
    dbTestResult.value = null;
  }
});
const dbFormModified = computed(
  () => dbChoice.value === 'change' && JSON.stringify(dbForm) !== dbFormSnapshot.value
);

const formStep2Ref = ref<FormInstance>();
const formStep3Ref = ref<FormInstance>();
const formStep4Ref = ref<FormInstance>();
const formStep5Ref = ref<FormInstance>();

const form = reactive({
  edition: 'PERSONAL',
  runningMode: 'SINGLE',
  registrationAuthorityName: '',
  registrationAuthorityEnglishName: '',
  registrationAuthorityEmail: '',
  selfAsRegistrar: true,
  createNamespace: true,
  namespaceName: '默认命名空间',
  identifierInputMode: 'AUTO_WITH_OVERRIDE',
  versionInputMode: 'AUTO_WITH_OVERRIDE',
  identifierStrategy: 'SEGMENT_TEMPLATE',
  // 默认编排：元数据对象缩写 + 分隔符 + 五位顺序号（如 DE.00001）；
  // identifierStrategy 固定为 SEGMENT_TEMPLATE，由编辑器产出模板 JSON。
  identifierTemplate: JSON.stringify({
    segments: [
      { type: 'OBJECT_ABBREVIATION', name: 'objectAbbr', params: {} },
      { type: 'SEPARATOR', params: { char: '.' } },
      { type: 'SEQUENCE', name: 'seq', params: { start: 1, increment: 1, pad: 5, overflow: 'REJECT' } },
    ],
  }),
  versionPolicy: 'SEMVER',
  createRegistrySpecification: true,
  registryName: '',
  defaultLanguage: 'zh-CN',
  registryDescription: '',
});

const pwdForm = reactive({
  newPassword: '',
  confirmPassword: '',
});

const rulesStep2 = reactive<FormRules>({
  registrationAuthorityName: [
    { required: true, message: '请输入注册机构名称', trigger: 'blur' },
    { min: 2, max: 100, message: '长度在 2 到 100 个字符', trigger: 'blur' }
  ],
  registrationAuthorityEnglishName: [
    { min: 2, max: 100, message: '长度在 2 到 100 个字符', trigger: 'blur' }
  ],
  registrationAuthorityEmail: [
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }
  ]
});

const rulesStep4 = reactive<FormRules>({
  registryName: [
    { required: true, message: '请输入注册规范名称', trigger: 'blur' },
    { min: 2, max: 100, message: '长度在 2 到 100 个字符', trigger: 'blur' }
  ],
  defaultLanguage: [
    { required: true, message: '请选择默认语言', trigger: 'change' }
  ]
});

/**
 * 标识模板校验：手动录入模式下豁免；自动模式下模板必须可解析，
 * 且恰好包含一个唯一性段（顺序号/时间戳/UUID）。
 */
function validateIdentifierTemplate(rule: any, value: any, callback: any) {
  if (form.identifierInputMode === 'MANUAL_ONLY') {
    callback();
    return;
  }
  if (!value) {
    callback(new Error('请在编辑器中配置标识模板'));
    return;
  }
  try {
    const parsed = JSON.parse(value);
    const segs: any[] = parsed.segments || [];
    const uniqueness = segs.filter((s: any) => ['SEQUENCE', 'TIMESTAMP', 'UUID'].includes(s.type));
    if (uniqueness.length !== 1) {
      callback(new Error('模板必须恰好包含一个唯一性段（顺序号/时间戳/UUID）'));
      return;
    }
    callback();
  } catch {
    callback(new Error('模板 JSON 格式不正确'));
  }
}

const rulesStep3 = reactive<FormRules>({
  namespaceName: [
    { required: true, message: '请输入命名空间名称', trigger: 'blur' },
    { min: 2, max: 100, message: '长度在 2 到 100 个字符', trigger: 'blur' }
  ],
  identifierTemplate: [
    { validator: validateIdentifierTemplate, trigger: 'change' }
  ],
  versionPolicy: [
    { required: true, message: '请选择版本生成策略', trigger: 'change' }
  ]
});

const validateConfirmPassword = (rule: any, value: any, callback: any) => {
  if (value === '') {
    callback(new Error('请再次输入新密码'));
  } else if (value !== pwdForm.newPassword) {
    callback(new Error('两次输入的密码不一致'));
  } else {
    callback();
  }
};

const rulesStep5 = reactive<FormRules>({
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度需在 6 到 20 个字符之间', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, validator: validateConfirmPassword, trigger: 'blur' }
  ]
});

const lastStepIndex = computed(() => {
  return 5; // Step 6 (admin password) is always last
});

// 步骤指示器：两个版本均包含治理配置步骤（注册机构 → 命名空间 → 注册规范），
// 这三者是开展数据治理工作的先决条件；个人版以预填默认值保持精简体验。
const displaySteps = computed(() => {
  return ['版本选择', '数据库', '注册机构', '命名空间', '注册规范', '管理员密码'];
});

const displayActive = computed(() => {
  return currentStep.value;
});

// Auto-fill registry name when RA name changes
watch(() => form.registrationAuthorityName, (newVal) => {
  if (newVal && !form.registryName.trim()) {
    form.registryName = newVal + '元数据注册表规范';
  }
});

// 个人版预填治理字段默认值（可修改）；团队版要求显式填写。
// 注意：必须注册在 registryName 联动 watcher 之后，immediate 回调对
// registrationAuthorityName 的赋值才能触发联动预填。
watch(() => form.edition, (val) => {
  if (val === 'PERSONAL' && !form.registrationAuthorityName.trim()) {
    form.registrationAuthorityName = '个人注册机构';
  }
}, { immediate: true });

// ── Network helpers ────────────────────────────────────────────────
async function callSetup(): Promise<void> {
  // Auto-fill registry name before submit
  if (form.createRegistrySpecification && !form.registryName.trim() && form.registrationAuthorityName.trim()) {
    form.registryName = form.registrationAuthorityName.trim() + '元数据注册表规范';
  }

  loading.value = true;
  try {
    const loginResponse = await request.post<{
      token: string;
      user: any;
      expiresInSeconds?: number;
    }>('/init-wizard/setup', form);
    auth.adoptSession({
      token: loginResponse.token,
      user: loginResponse.user,
    });
  } finally {
    loading.value = false;
  }
}

async function callAbort(): Promise<void> {
  try {
    await request.post('/init-wizard/abort');
  } catch (e) {
    // Ignore — abort is best-effort, especially when no data has been
    // written yet.
  }
}

// ── Step navigation ────────────────────────────────────────────────
function prevStep() {
  if (currentStep.value > 0) {
    currentStep.value--;
  }
}

async function nextStep() {
  if (currentStep.value === 0) {
    currentStep.value = 1;
    return;
  }

  if (currentStep.value === 1) {
    // 数据库步骤：保存待重启则阻止继续；选择"配置新的数据源"且修改过参数时，
    // "下一步"即保存数据源配置（保存后需重启，由 restartRequired 接管界面）；
    // 未做任何修改时与"保持当前数据源并继续"相同，直接进入下一步。
    if (restartRequired.value) return;
    if (dbChoice.value === 'change' && dbFormModified.value) {
      if (!dbTestResult.value?.success) {
        ElMessage.error('请先测试连接，确认新数据源可用后再继续');
        return;
      }
      loading.value = true;
      try {
        await saveDbConfig();
      } catch {
        // 拦截器已提示错误，留在本步骤
      } finally {
        loading.value = false;
      }
      return;
    }
    if (form.edition === 'TEAM' && currentDb.value?.vendor === 'sqlite') {
      ElMessage.error('团队版不支持 SQLite，请配置 SQL Server 或达梦数据库');
      return;
    }
    currentStep.value = 2;
    return;
  }

  if (currentStep.value === 2) {
    if (!formStep2Ref.value) return;
    let isValid = false;
    try {
      isValid = await formStep2Ref.value.validate();
    } catch {
      isValid = false;
    }
    if (isValid) currentStep.value = 3;
    return;
  }

  if (currentStep.value === 3) {
    if (form.createNamespace) {
      if (!formStep3Ref.value) return;
      let isValid = false;
      try {
        isValid = await formStep3Ref.value.validate();
      } catch {
        isValid = false;
      }
      if (!isValid) return;
    }
    currentStep.value = 4;
    return;
  }

  if (currentStep.value === 4) {
    if (form.createRegistrySpecification) {
      if (!formStep4Ref.value) {
        ElMessage.error('注册规范表单未就绪，请刷新页面重试');
        return;
      }
      let isValid = false;
      try {
        isValid = await formStep4Ref.value.validate();
      } catch {
        isValid = false;
      }
      if (!isValid) {
        ElMessage.error('请完成注册规范的所有必填项');
        return;
      }
    }
    // Submit the entire wizard configuration to the backend. The backend
    // writes business data but does NOT flip system.initialized — that
    // happens only on commit() in step 5. If anything below fails, the
    // onBeforeRouteLeave / onBeforeUnmount abort() will roll back the
    // business data.
    try {
      await callSetup();
      currentStep.value = 5;
    } catch (e) {
      // Interceptor surfaces the error; stay on step 4 so the user can fix
      // the form.
    }
    return;
  }
}

// ── Datasource step helpers ──────────────────────────────────────
async function loadCurrentDb() {
  try {
    currentDb.value = await request.get<CurrentDb>('/datasource/config');
  } catch {
    currentDb.value = null;
  }
}

async function testDbConnection() {
  dbTesting.value = true;
  dbTestResult.value = null;
  try {
    const result = await request.post<DbTestResult>('/datasource/test-connection', {
      vendor: dbForm.vendor,
      host: dbForm.host,
      port: dbForm.port,
      database: dbForm.database,
      filePath: dbForm.filePath,
      username: dbForm.username || null,
      password: dbForm.password || null,
      edition: form.edition,
    });
    dbTestResult.value = result;
  } catch (e) {
    const err = e as any;
    dbTestResult.value = {
      success: false,
      message: err?.response?.data?.message || err?.message || '连接失败，请检查参数',
    };
  } finally {
    dbTesting.value = false;
  }
}

async function saveDbConfig() {
  dbSaving.value = true;
  try {
    await request.put('/datasource/config', {
      vendor: dbForm.vendor,
      host: dbForm.host,
      port: dbForm.port,
      database: dbForm.database,
      filePath: dbForm.filePath,
      username: dbForm.username || null,
      password: dbForm.password || null,
      edition: form.edition,
    });
    restartRequired.value = true;
  } finally {
    dbSaving.value = false;
  }
}

async function handleCommit() {
  if (!formStep5Ref.value) return;
  let isValid = false;
  try {
    isValid = await formStep5Ref.value.validate();
  } catch {
    isValid = false;
  }
  if (!isValid) {
    ElMessage.error('请检查新密码是否符合要求（6-20 位，两次输入一致）');
    return;
  }

  loading.value = true;
  commitError.value = '';
  try {
    await request.post('/init-wizard/commit', {
      newPassword: pwdForm.newPassword,
      confirmPassword: pwdForm.confirmPassword,
    });
    setupCommitted.value = true;
    // The wizard session token carries forceChangePassword=true and stays
    // stale even though commit() cleared the flag server-side — every API
    // call with it would be rejected with 498. Drop it and send the admin
    // to login so they get a clean token with their brand-new password.
    auth.logout();
    ElMessage.success('系统初始化成功！请使用新密码登录');
    router.push('/login');
  } catch (e) {
    // The interceptor shows a transient toast; also pin the reason inline so
    // the user cannot miss why the final commit failed.
    const err = e as any;
    commitError.value =
      err?.response?.data?.message || err?.message || '提交失败，请稍后重试';
  } finally {
    loading.value = false;
  }
}

// ── Interrupt detection: any leave path must abort the wizard ──────
//
// Three vectors:
//   1) vue-router navigation away from /init-wizard
//   2) browser tab close / refresh / back button
//   3) tab switch (visibilitychange → hidden) — we don't auto-abort on
//      visibilitychange alone (users often switch tabs), only when they
//      return to the wizard and find stale data we ask them to confirm
//      starting over.
onBeforeRouteLeave(async () => {
  if (setupCommitted.value) return true;
  try {
    await ElMessageBox.confirm(
      '离开将放弃初始化并清除已写入的业务数据。确定离开吗？',
      '放弃初始化',
      { confirmButtonText: '放弃并离开', cancelButtonText: '继续配置', type: 'warning' }
    );
  } catch {
    return false;
  }
  await callAbort();
  auth.setInitialized(false);
  auth.user = null;
  return true;
});

// Native beforeunload: browsers ignore the return value but show a
// confirmation dialog. Combined with our onBeforeRouteLeave this gives
// reasonable protection.
function onBeforeUnload(e: BeforeUnloadEvent) {
  if (setupCommitted.value) return;
  e.preventDefault();
  e.returnValue = '';
}

onMounted(() => {
  window.addEventListener('beforeunload', onBeforeUnload);
  loadCurrentDb();
});

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', onBeforeUnload);
});
</script>

<style scoped lang="scss">
.wizard-card {
  width: 780px;
  max-width: 100%;
  border-radius: 16px;
  border: 1px solid rgba(255, 255, 255, 0.5);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.15);

  :deep(.el-card__body) {
    padding: 40px;
  }
}

.wizard-header {
  text-align: center;
  margin-bottom: 32px;

  img {
    filter: drop-shadow(0 4px 8px rgba(37, 99, 235, 0.25));
  }

  h2 {
    margin: 16px 0 6px;
    font-size: 26px;
    font-weight: 700;
    color: #1e3a8a;
  }

  p {
    margin: 0;
    color: #64748b;
    font-size: 14px;
  }
}

.final-step-alert {
  margin: 0 0 24px;
  background-color: #fff7ed;
  border: 1px solid #fdba74;
  border-left: 4px solid #ea580c;

  :deep(.el-alert__title) {
    color: #9a3412;
    font-size: 14px;
    line-height: 1.6;
  }
}

:deep(.el-steps.final-step-active .el-step.is-process) {
  // Highlight current step
  color: #ea580c;
}

:deep(.el-step__head.is-process) {
  color: #2563eb;
  border-color: #2563eb;
}

:deep(.el-step__title.is-process) {
  font-weight: 600;
  color: #2563eb;
}

.step-content {
  min-height: 280px;
  margin: 32px 0;
}

.step-pane {
  display: flex;
  flex-direction: column;
}

.step-title {
  margin: 0 0 6px;
  font-size: 18px;
  font-weight: 600;
  color: #1e293b;
}

.step-desc {
  margin: 0 0 24px;
  color: #64748b;
  font-size: 14px;
}

.mode-options {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.mode-card {
  display: flex;
  align-items: center;
  padding: 20px;
  border-radius: 12px;
  border: 2px solid #e2e8f0;
  transition: all 0.3s ease;
  position: relative;

  &.active {
    border-color: #2563eb;
    background-color: #eff6ff;
    cursor: pointer;

    .mode-icon-wrapper {
      background-color: #2563eb;
      color: #ffffff;
    }
  }

  &.disabled {
    opacity: 0.65;
    background-color: #f8fafc;
    border-color: #e2e8f0;
    cursor: not-allowed;

    .mode-icon-wrapper {
      background-color: #cbd5e1;
      color: #94a3b8;
    }
  }

  &:not(.disabled):hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(37, 99, 235, 0.1);
  }
}

.mode-icon-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 10px;
  margin-right: 20px;
  flex-shrink: 0;
}

.mode-info {
  flex: 1;

  h4 {
    margin: 0 0 6px;
    font-size: 16px;
    font-weight: 600;
    color: #1e293b;
  }

  p {
    margin: 0;
    font-size: 13px;
    line-height: 1.5;
    color: #64748b;
  }
}

.mode-badge-active {
  display: flex;
  align-items: center;
  gap: 4px;
  position: absolute;
  top: 16px;
  right: 16px;
  font-size: 12px;
  font-weight: 600;
  color: #2563eb;
  background-color: #dbeafe;
  padding: 4px 10px;
  border-radius: 99px;
}

.mode-badge-tbd {
  position: absolute;
  top: 16px;
  right: 16px;
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
  background-color: #f1f5f9;
  padding: 4px 10px;
  border-radius: 99px;
}

.wizard-form {
  max-width: 560px;
  margin: 0 auto;
}

.wizard-settings-box {
  background-color: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 20px;
}

.setting-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 24px;

  .setting-label-section {
    flex: 1;

    h4 {
      margin: 0 0 6px;
      font-size: 15px;
      font-weight: 600;
      color: #1e293b;
    }

    p {
      margin: 0;
      font-size: 13px;
      color: #64748b;
      line-height: 1.4;
    }
  }
}

.wizard-footer {
  display: flex;
  margin-top: 32px;
  border-top: 1px solid #e2e8f0;
  padding-top: 24px;
}

.fade-in {
  animation: fadeIn 0.35s ease-in-out;
}

.db-current-card {
  margin-bottom: 16px;
  background: #f8fafc;
}

.db-current-title {
  font-weight: 600;
  margin-bottom: 4px;
}

.db-current-line {
  font-size: 13px;
  color: #606266;
  word-break: break-all;
}

.db-choice {
  margin-bottom: 16px;
  display: flex;
  gap: 24px;
}

.db-test-result {
  margin-left: 12px;
  color: #f56c6c;

  &.ok {
    color: #67c23a;
  }
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
