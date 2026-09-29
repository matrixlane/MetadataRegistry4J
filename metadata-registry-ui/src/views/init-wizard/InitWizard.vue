<template>
  <div class="login-layout">
    <el-card class="wizard-card" shadow="always">
      <div class="wizard-header">
        <img src="/favicon.svg" width="56" height="56" alt="logo" />
        <h2>初始化配置向导</h2>
        <p>系统首次部署完成，请按照以下步骤完成基础配置</p>
      </div>

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

        <!-- Step 3: Registration Authority + Registrar Details -->
        <div v-if="currentStep === 2" class="step-pane fade-in">
          <h3 class="step-title">配置默认注册机构</h3>
          <p class="step-desc">注册机构是根据标准开展元数据管理和数据治理的最高权力组织机构</p>

          <el-divider content-position="left">① 配置注册机构</el-divider>
          <el-form
            label-position="top"
            class="wizard-form"
          >
            <el-form-item label="名称">
              <el-input
                v-model="form.registrationAuthorityName"
                placeholder="例如：信息化管理部"
                size="large"
              >
                <template #prefix>
                  <el-icon><OfficeBuilding /></el-icon>
                </template>
              </el-input>
            </el-form-item>

            <el-form-item label="文档语言">
              <el-select
                v-model="form.registrationAuthorityDocumentLanguages"
                multiple
                filterable
                placeholder="至少选择 1 个语言"
                style="width: 100%;"
                size="large"
              >
                <el-option
                  v-for="lang in languageOptions"
                  :key="lang.languageIdentifier"
                  :label="languageOptionLabel(lang)"
                  :value="lang.languageIdentifier"
                />
              </el-select>
            </el-form-item>
          </el-form>

          <el-divider content-position="left">② 配置注册人员</el-divider>
          <div class="wizard-settings-box">
            <!-- 个人版：admin 自动兼任 Registrar（前端固定为只读"自动"，后端强制 selfAsRegistrar=true）；
                 团队版提供开关由管理员显式决定 -->
            <div v-if="form.edition === 'TEAM'" class="setting-item">
              <div class="setting-label-section">
                <h4>将当前超级管理员设为该机构的注册人员</h4>
                <p>勾选后，当前登录的超级管理员将直接兼任该注册机构的注册人员，从而拥有在该机构下创建命名空间、导入联系人的权限。</p>
              </div>
              <el-switch v-model="form.selfAsRegistrar" size="large" />
            </div>
            <div v-else class="setting-item">
              <div class="setting-label-section">
                <h4>当前超级管理员自动成为该机构的注册人员</h4>
                <p>个人版下无需选择：当前超级管理员将自动兼任该注册机构的注册人员。</p>
              </div>
              <el-tag type="success" effect="dark" size="large">自动</el-tag>
            </div>
          </div>

          <el-form
            label-position="top"
            class="wizard-form"
          >
            <el-form-item label="注册人员角色">
              <el-input
                v-model="form.adminRoleName"
                :placeholder="form.edition === 'PERSONAL' ? '默认：注册人员' : '请输入角色名称（必填）'"
                size="large"
              >
                <template #prefix>
                  <el-icon><Medal /></el-icon>
                </template>
              </el-input>
            </el-form-item>

            <el-form-item label="注册人员姓名">
              <el-input
                v-model="form.adminIndividualName"
                placeholder="选填"
                size="large"
              >
                <template #prefix>
                  <el-icon><User /></el-icon>
                </template>
              </el-input>
            </el-form-item>
          </el-form>
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
            class="wizard-form wizard-form-wide"
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
          <p class="step-desc">注册规范描述注册表的运行环境，是注册表的固有组成部分。以下为系统默认配置，可按部署实际调整</p>

          <el-form
            ref="formStep4Ref"
            :model="form"
            :rules="rulesStep4"
            label-position="top"
            class="wizard-form"
          >
            <el-form-item label="注册规范名称" prop="registryName">
              <el-input
                v-model="form.registryName"
                placeholder="例如：主元数据注册表规范"
                size="large"
                clearable
              />
            </el-form-item>

            <el-form-item label="注释">
              <el-input
                v-model="form.registryDescription"
                type="textarea"
                :rows="2"
                placeholder="关于此注册规范的简要说明"
              />
            </el-form-item>

            <el-form-item label="网址">
              <el-input
                v-model="form.registryWebAddress"
                placeholder="注册表的 Web 地址（可选）"
                size="large"
              />
            </el-form-item>

            <el-form-item label="默认时区" prop="registryDefaultTimeZone">
              <el-input v-model="form.registryDefaultTimeZone" placeholder="[-+]HHMM，如 +0800、-0230" size="large" />
            </el-form-item>

            <el-form-item label="默认语言">
              <el-select v-model="form.defaultLanguage" size="large" style="width: 100%;">
                <el-option
                  v-for="lang in languageOptions"
                  :key="lang.languageIdentifier"
                  :label="languageOptionLabel(lang)"
                  :value="lang.languageIdentifier"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="字符集" prop="registryCharacterRepertoire">
              <el-input v-model="form.registryCharacterRepertoire" size="large" />
            </el-form-item>

            <el-form-item label="标准">
              <el-input v-model="form.registryStandard" size="large" />
            </el-form-item>

            <el-form-item label="合规程度">
              <el-select v-model="form.registryDegreeOfConformance" clearable size="large" style="width: 100%;">
                <el-option label="合规" value="CONFORMING" />
                <el-option label="严格合规" value="STRICTLY_CONFORMING" />
              </el-select>
            </el-form-item>

          </el-form>
        </div>

        <!-- Step 6: Set Admin Password — final commit step -->
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
                placeholder="8-64 个字符"
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
import { Setting, OfficeBuilding, Check, User, Medal } from '@element-plus/icons-vue';
import request, { withSilentErrors } from '@/utils/request';
import { useAuthStore } from '@/stores/auth';
import { languageApi } from '@/api/resources';
import SegmentTemplateDesigner from '@/components/identifier/SegmentTemplateDesigner.vue';
import { timeZoneValidator } from '@/views/registry-spec/registrySpecForm';

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
  host?: string | null;
  port?: number | null;
  database?: string | null;
  filePath?: string | null;
  hasCredentials?: boolean;
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

// 切到"配置新的数据源"时把表单预填为当前数据源配置，再打快照；
// "下一步"时若表单与快照一致（没有做过任何修改），等价于"保持当前数据源并继续"，
// 不保存也不重启
const dbFormSnapshot = ref('');

function prefillDbFormFromCurrent() {
  const cur = currentDb.value;
  if (!cur) return;
  if (cur.vendor === 'sqlite' || cur.vendor === 'sqlserver' || cur.vendor === 'dm') {
    dbForm.vendor = cur.vendor;
  }
  if (cur.vendor === 'sqlite') {
    if (cur.filePath) dbForm.filePath = cur.filePath;
  } else {
    if (cur.host) dbForm.host = cur.host;
    if (cur.port) dbForm.port = cur.port;
    if (cur.database) dbForm.database = cur.database;
  }
  // 凭据不回传（hasCredentials 仅作标记），统一留空由用户按需填写
  dbForm.username = '';
  dbForm.password = '';
}

watch(dbChoice, (choice) => {
  if (choice === 'change') {
    prefillDbFormFromCurrent();
    dbFormSnapshot.value = JSON.stringify(dbForm);
    dbTestResult.value = null;
  }
});

// 当前数据源是异步加载的：若用户先切到"配置新的数据源"后配置才加载完成，
// 且表单尚未被修改，则补一次预填并刷新快照
watch(currentDb, (cur) => {
  if (cur && dbChoice.value === 'change' && !dbFormModified.value) {
    prefillDbFormFromCurrent();
    dbFormSnapshot.value = JSON.stringify(dbForm);
  }
});

const dbFormModified = computed(
  () => dbChoice.value === 'change' && JSON.stringify(dbForm) !== dbFormSnapshot.value
);

const formStep3Ref = ref<FormInstance>();
const formStep4Ref = ref<FormInstance>();
const formStep5Ref = ref<FormInstance>();

const form = reactive({
  edition: 'PERSONAL',
  runningMode: 'SINGLE',
  registrationAuthorityName: '',
  // ISO/IEC 11179-3:2023 表 24 — documentation_language_identifier (1..*)
  registrationAuthorityDocumentLanguages: ['zh-CN'] as string[],
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
  // ── Step 5 注册规范（ISO/IEC 11179-3 表 28）──
  registryName: '',
  registryDescription: '',
  registryWebAddress: '',
  registryCharacterRepertoire: 'UTF-8',
  registryStandard: 'ISO/IEC 11179-3:2023',
  registryDegreeOfConformance: undefined as string | undefined,
  registryConformanceLevel: '',
  registryConformanceLabel: '',
  registryIncludedFeatures: '',
  registryExcludedFeatures: '',
  registryDefaultTimeZone: '+0800',
  defaultLanguage: 'zh-CN',
  registryAlternativeLanguages: [] as string[],
  registryRepresentationClassSchemeId: '',
  registryReferenceDocumentIdentifierKinds: [] as string[],
  // ── 注册管理员三要素（add-registrar-three-elements）──
  // 所属组织不在此处选择：RegistrationAuthority extends Organization 且 JOINED 共表，
  // 注册机构本身即组织，初始化阶段系统内不存在其他组织；后端恒取本次创建的 RA。
  adminIndividualName: '',
  adminRoleName: '',
});

// ISO/IEC 11179-3:2023 表 6 推荐使用 ISO 639-2 三字母编码；
// 实际可选范围取自已登记且启用的语言条目（value 为 BCP 47 标识符，label 为「中文名（标识符）」）。
interface LanguageOption { languageIdentifier: string; languageName?: string }
const languageOptions = ref<LanguageOption[]>([]);

/** 语言下拉显示项：中文名（BCP 47 标识符）；无中文名时只显示标识符。 */
function languageOptionLabel(lang: LanguageOption): string {
  return lang.languageName ? `${lang.languageName}（${lang.languageIdentifier}）` : lang.languageIdentifier;
}

const pwdForm = reactive({
  newPassword: '',
  confirmPassword: '',
});

const rulesStep4 = reactive<FormRules>({
  registryName: [
    { required: true, message: '请输入注册规范名称', trigger: 'blur' },
    { min: 2, max: 100, message: '长度在 2 到 100 个字符', trigger: 'blur' }
  ],
  registryCharacterRepertoire: [
    { required: true, message: '请输入字符集', trigger: 'blur' }
  ],
  registryDefaultTimeZone: [
    { validator: timeZoneValidator, trigger: 'blur' }
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
    { min: 8, max: 64, message: '密码长度需在 8-64 个字符之间', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, validator: validateConfirmPassword, trigger: 'blur' }
  ]
});

const lastStepIndex = computed(() => {
  return 5; // Step 6 (admin password) is always last
});

// 步骤指示器：两个版本均包含治理配置步骤（注册机构 → 命名空间 → 注册规范），
// 这三者是开展数据治理工作的先决条件；注册规范由向导配置表 28 属性、系统
// 以默认值预填，个人版同样以预填默认值保持精简体验。
const displaySteps = computed(() => {
  return ['版本选择', '数据库', '注册机构', '命名空间', '注册规范', '管理员密码'];
});

const displayActive = computed(() => {
  return currentStep.value;
});

// Pre-fill registry specification name with a stable default regardless of
// how the registration authority is named — the wizard does not chain the
// two so users see a deterministic, easy-to-edit value instead of a
// generated string that fights edits to either field.
watch(() => form.registrationAuthorityName, (newVal) => {
  if (newVal && !form.registryName.trim()) {
    form.registryName = '默认注册规范';
  }
});

// 个人版预填治理字段默认值（可修改）；团队版要求显式填写。
// 个人版下 Admin 自动成为 Registrar（后端强制 selfAsRegistrar=true），前端同步锁定为 true。
// 注册机构(Registration_Authority)是组织机构层级，默认名"默认注册机构"；
// Registrar 是 admin 兼任的注册管理员（与组织机构区分），名称由绑定流程赋予。
// 注册人员姓名在 PERSONAL 版下保持空白（选填）；后端对空值不再兜底，
// 未填写则不创建 Individual，由用户后续在"我的"中填写并回填 Registrar。
watch(() => form.edition, (val) => {
  if (val === 'PERSONAL') {
    if (!form.registrationAuthorityName.trim()) {
      form.registrationAuthorityName = '默认注册机构';
    }
    if (!form.registrationAuthorityDocumentLanguages.length) {
      form.registrationAuthorityDocumentLanguages = ['zh-CN'];
    }
    if (!form.adminRoleName.trim()) {
      form.adminRoleName = '注册管理员';
    }
    form.selfAsRegistrar = true;
  } else {
    // TEAM 版切到时清空 PERSONAL 留的角色默认（让用户显式填），保留他们输入过的真实值
    if (form.adminRoleName.trim() === '注册管理员') {
      form.adminRoleName = '';
    }
    if (form.registrationAuthorityDocumentLanguages.length === 1 && form.registrationAuthorityDocumentLanguages[0] === 'zh-CN') {
      form.registrationAuthorityDocumentLanguages = [];
    }
  }
}, { immediate: true });

// ── Network helpers ────────────────────────────────────────────────
async function callSetup(): Promise<void> {
  loading.value = true;
  try {
    const {
      registryWebAddress,
      registryCharacterRepertoire,
      registryStandard,
      registryDegreeOfConformance,
      registryConformanceLevel,
      registryConformanceLabel,
      registryIncludedFeatures,
      registryExcludedFeatures,
      registryDefaultTimeZone,
      registryAlternativeLanguages,
      registryReferenceDocumentIdentifierKinds,
      registryRepresentationClassSchemeId,
      ...base
    } = form;
    const payload = {
      ...base,
      // ISO/IEC 11179-3:2023 表 24 documentation_language_identifier (1..*)
      registrationAuthorityDocumentLanguages: form.registrationAuthorityDocumentLanguages.length
        ? [...form.registrationAuthorityDocumentLanguages]
        : null,
      // add-registrar-three-elements: 注册管理员姓名与角色；所属组织由后端取本次创建的 RA
      adminIndividualName: (form.adminIndividualName || '').trim() || null,
      adminRoleName: (form.adminRoleName || '').trim() || null,
      registrySpecificationAttributes: {
        webAddress: registryWebAddress || null,
        characterRepertoire: registryCharacterRepertoire || null,
        standard: registryStandard || null,
        degreeOfConformance: registryDegreeOfConformance || null,
        conformanceLevel: registryConformanceLevel || null,
        conformanceLabel: registryConformanceLabel || null,
        includedFeatures: registryIncludedFeatures || null,
        excludedFeatures: registryExcludedFeatures || null,
        defaultTimeZone: registryDefaultTimeZone || null,
        alternativeLanguages: registryAlternativeLanguages.length ? registryAlternativeLanguages : null,
        referenceDocumentIdentifierKinds: registryReferenceDocumentIdentifierKinds.length
          ? registryReferenceDocumentIdentifierKinds
          : null,
        representationClassSchemeId: registryRepresentationClassSchemeId || null,
      },
    };
    const loginResponse = await request.post<{
      token: string;
      user: any;
      expiresInSeconds?: number;
    }>('/init-wizard/setup', payload);
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
    // 注册机构字段手工校验（与下方注册人员字段保持一致风格，避免 el-form 校验失败时高度抖动）
    if (!form.registrationAuthorityName.trim()) {
      ElMessage.error('请输入注册机构名称');
      return;
    }
    if (!form.registrationAuthorityDocumentLanguages.length) {
      ElMessage.error('请至少选择 1 个文档语言');
      return;
    }
    // add-registrar-three-elements: TEAM 版下角色必填，但仅在确实要创建 Registrar 时。
    // 关闭「设为注册人员」后不创建 Registrar，不应要求其要素（与 spec system-initialization 对齐）。
    if (form.edition === 'TEAM' && form.selfAsRegistrar) {
      if (!form.adminRoleName.trim()) {
        ElMessage.error('请填写注册人员角色');
        return;
      }
    }
    currentStep.value = 3;
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
    // Submit the entire wizard configuration to the backend. The backend
    // writes business data (registration authority, namespace, and the
    // registry specification with the configured attributes) but does NOT
    // flip system.initialized — that happens only on commit() in the final
    // step. If anything below fails, the onBeforeRouteLeave /
    // onBeforeUnmount abort() will roll back the business data.
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
    ElMessage.error('请检查新密码是否符合要求（8-64 位，两次输入一致）');
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

onMounted(async () => {
  window.addEventListener('beforeunload', onBeforeUnload);
  loadCurrentDb();
  // 加载可选语言（文档语言与默认语言共用；value 为 BCP 47 标识符，label 为「中文名（标识符）」）
  // 静默加载：拉取失败不阻塞向导，两个下拉留空由用户继续操作。
  try {
    const langRes = await withSilentErrors(async () => (await languageApi.list({ size: 200, enabled: true })) ?? null);
    languageOptions.value = (langRes?.content ?? [])
      .map((l: any) => ({
        languageIdentifier: l.languageIdentifier || '',
        languageName: l.languageName ?? undefined,
      }))
      .filter((l: LanguageOption) => !!l.languageIdentifier);
  } catch {
    languageOptions.value = [];
  }
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
    filter: drop-shadow(0 4px 8px rgba(22, 163, 74, 0.25));
  }

  h2 {
    margin: 16px 0 6px;
    font-size: 26px;
    font-weight: 700;
    color: #1e4530;
  }

  p {
    margin: 0;
    color: #64748b;
    font-size: 14px;
  }
}

:deep(.el-steps.final-step-active .el-step.is-process) {
  // Highlight current step
  color: #ea580c;
}

:deep(.el-step__head.is-process) {
  color: var(--el-color-primary);
  border-color: var(--el-color-primary);
}

:deep(.el-step__title.is-process) {
  font-weight: 600;
  color: var(--el-color-primary);
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
    border-color: var(--el-color-primary);
    background-color: #dcfce7;
    cursor: pointer;

    .mode-icon-wrapper {
      background-color: #16a34a;
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
    box-shadow: 0 4px 12px rgba(22, 163, 74, 0.1);
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
  color: var(--el-color-primary);
  background-color: #dcfce7;
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
  // .step-pane 是 column flex 容器，交叉轴上的 auto 外边距会让 flex 子项放弃 stretch、
  // 退回 fit-content 尺寸，表单列宽改由各控件的内容宽度决定。显式给出确定列宽后，
  // 所有控件的 100% 才指向同一个值，「文档语言」下拉不再比其它输入框窄。
  width: 255px;
  max-width: 100%;
  box-sizing: border-box;
  margin: 0 auto;
  // el-select 根节点的宽度取自该变量（theme-chalk 里默认 100%，但允许被主题或组件内联样式改写），
  // 显式钉死为 100%，避免下拉框走自己的宽度来源。
  --el-select-width: 100%;

  // 表单项内容区默认是 flex 容器，各控件作为 flex item 参与行内分配（min-width:auto 会阻止收缩到
  // 100% 以下）。改为 block 后控件按块级填充内容列，输入框与下拉框共用同一个列宽基准。
  :deep(.el-form-item__content) {
    display: block !important;
  }

  // 元素 plus 2.x 的 el-input 是双层 inline-flex 容器（.el-input > .el-input__wrapper > .el-input__inner），
  // 且 .el-input__suffix 默认 flex-shrink: 0。当 clearable 按钮在 focused 时显示后，
  // suffix 区域被撑大、wrapper 跟着撑大、inline-flex 的 .el-input 因 content-driven width 越过 100%，
  // 整体向右扩展约 50px。这里用 display: block + width:100% + max-width:100% + min-width:0
  // 锁死三个层级严格不超出父容器，禁用 wrapper 的 GPU transform，强制文字 / placeholder 左对齐。
  :deep(.el-input) {
    display: block !important;
    width: 100% !important;
    max-width: 100% !important;
    min-width: 0 !important;
    // 必须在这里（而不是只依赖文件末尾的非 scoped 块）声明 border-box：
    // el-input 默认 content-box，width:100% 会再加上左右内边距（约 32px），
    // 使输入框比同列的 el-select 宽一截。
    box-sizing: border-box !important;
  }
  // el-select / el-input-number 根节点默认是 inline-block，走的是另一条内在宽度计算路径，
  // 不加约束时会比 el-input 窄。与 el-input 同样锁成 block + 100% + border-box。
  :deep(.el-select),
  :deep(.el-input-number) {
    display: block !important;
    width: 100% !important;
    max-width: 100% !important;
    min-width: 0 !important;
    box-sizing: border-box !important;
  }
  :deep(.el-select__wrapper) {
    width: 100% !important;
    max-width: 100% !important;
    min-width: 0 !important;
    box-sizing: border-box !important;
  }
  :deep(.el-input__wrapper) {
    display: flex !important;
    width: 100% !important;
    max-width: 100% !important;
    min-width: 0 !important;
    flex-grow: 0 !important;
    flex-shrink: 1 !important;
    transform: none !important;
    // 与 .el-select__wrapper 保持同一盒模型：wrapper 默认 content-box，
    // width:100% 还会再加左右内边距（各 15px），导致输入框可见边框比同列下拉框宽 30px
    // （根节点同宽时下拉显得「短一截」）。统一 border-box 后两者可见边框同宽。
    box-sizing: border-box !important;
  }
  :deep(.el-input__inner) {
    width: 100% !important;
    box-sizing: border-box !important;
    text-align: left !important;
  }
  :deep(.el-input__inner::placeholder) {
    text-align: left !important;
  }
  :deep(.el-select__placeholder) {
    text-align: left !important;
  }
  // suffix 区域允许收缩（覆盖 element plus 默认的 flex-shrink: 0），
  // 避免 clearable 按钮显示时把 wrapper / el-input 撑大。
  :deep(.el-input__suffix) {
    flex-shrink: 1 !important;
  }
  :deep(.el-input__prefix) {
    flex-shrink: 1 !important;
  }
}

// 命名空间步骤包含 SegmentTemplateDesigner 复合组件（预设库下拉、段编辑、映射表），
// 255px 基础列宽过窄；该步表单使用宽列，内部控件沿用 .wizard-form 的 100% 锁宽规则。
.wizard-form-wide {
  width: 560px;
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

<style lang="scss">
// 关键修复（不 scoped，避开 Vue scoped CSS 的 [data-v-xxx] 干扰）：
// Element Plus 2.x 的 .el-input__suffix 默认 flex-shrink: 0。当 el-input 设置了 clearable
// 且有值时，focused 状态下 .el-input__suffix 内会渲染清空图标（× 按钮），
// 让 suffix 区域被撑大；.el-input__wrapper 默认 display: inline-flex + flex-grow: 1
// 跟着扩展；最外层 .el-input 也是 inline-flex + width: 100%，当内部内容超过 100% 时
// 会按 max-content 撑大容器，最终整个 input 框向右扩展约 50px。
// 这里用全局 CSS 强制锁死三个层级的尺寸（block 容器 + width 100% + max-width 100% + min-width 0），
// 并让 wrapper / suffix / prefix 允许收缩（flex-shrink: 1），
// 同时禁用 wrapper 的 GPU 加速 transform（避免合成层尺寸重算）。
// 选择器用 .wizard-form 作为命名空间，仅作用于向导内 el-form，不污染项目其他位置。
.wizard-form .el-input,
.wizard-form .el-input.is-focus,
.wizard-form .el-input--suffix,
.wizard-form .el-input--prefix {
  display: block !important;
  width: 100% !important;
  max-width: 100% !important;
  min-width: 0 !important;
  box-sizing: border-box !important;
}
.wizard-form .el-input__wrapper,
.wizard-form .el-input__wrapper.is-focus {
  display: flex !important;
  width: 100% !important;
  max-width: 100% !important;
  min-width: 0 !important;
  flex-grow: 0 !important;
  flex-shrink: 1 !important;
  transform: none !important;
  // 统一盒模型：避免 content-box 下 width:100% 再加内边距，使可见边框比 el-select 宽 30px
  box-sizing: border-box !important;
}
.wizard-form .el-input__suffix,
.wizard-form .el-input__prefix {
  flex-shrink: 1 !important;
}
.wizard-form .el-input__inner {
  width: 100% !important;
  box-sizing: border-box !important;
  text-align: left !important;
}
.wizard-form .el-input__inner::placeholder {
  text-align: left !important;
}
.wizard-form .el-select__placeholder {
  text-align: left !important;
}
// 与上面的 .el-input 同理：el-select / el-input-number 根节点和 select 的 wrapper
// 一并锁成 block + width 100% + border-box，否则下拉框会按自身内容宽度收缩、比同行输入框窄。
// 同时把表单项内容区从 flex 改为 block，并钉死 --el-select-width，
// 消除 flex item 分配和组件内联宽度这两条独立的「下拉框更窄」来源。
.wizard-form .el-select,
.wizard-form .el-input-number {
  display: block !important;
  width: 100% !important;
  max-width: 100% !important;
  min-width: 0 !important;
  box-sizing: border-box !important;
}
.wizard-form .el-select__wrapper {
  width: 100% !important;
  max-width: 100% !important;
  min-width: 0 !important;
  box-sizing: border-box !important;
}
.wizard-form .el-form-item__content {
  display: block !important;
}
</style>
