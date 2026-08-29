<template>
  <div class="system-settings-page">
    <h2 style="margin-top: 0;">系统设置</h2>

    <!-- General Settings -->
    <el-card style="margin-bottom: 20px;" shadow="never">
      <template #header>
        <div style="font-weight: 600; font-size: 16px; display: flex; align-items: center; gap: 8px;">
          <el-icon color="var(--el-color-primary)"><Setting /></el-icon>
          <span>通用配置</span>
        </div>
      </template>

      <div style="display: flex; align-items: center; justify-content: space-between;">
        <div>
          <div style="font-size: 14px; font-weight: 600; margin-bottom: 4px;">新注册用户审核</div>
          <div style="font-size: 13px; color: var(--el-text-color-regular);">
            开启后，自助注册的新用户默认处于未启用状态，需要管理员审核通过后方可登录。
          </div>
        </div>
        <el-switch
          v-model="newUserRequireAudit"
          :loading="loadingSettings"
          @change="handleAuditSettingChange"
        />
      </div>

      <el-divider style="margin: 20px 0;" />

      <div style="display: flex; align-items: center; justify-content: space-between;">
        <div>
          <div style="font-size: 14px; font-weight: 600; margin-bottom: 4px;">允许注册新用户</div>
          <div style="font-size: 13px; color: var(--el-text-color-regular);">
            开启后，登录页将显示“注册”入口，允许外部用户自助注册账号。关闭后，仅管理员可创建用户。
          </div>
        </div>
        <el-switch
          v-model="allowUserRegistration"
          :loading="loadingSettings"
          @change="handleAllowRegistrationChange"
        />
      </div>
    </el-card>

    <!-- Datasource Settings -->
    <el-card style="margin-bottom: 20px;" shadow="never">
      <template #header>
        <div style="font-weight: 600; font-size: 16px; display: flex; align-items: center; gap: 8px;">
          <el-icon color="var(--el-color-primary)"><Coin /></el-icon>
          <span>数据源</span>
        </div>
      </template>

      <div v-if="currentDb">
        <div style="font-size: 13px; color: var(--el-text-color-regular); line-height: 1.8;">
          <div>方言：<strong>{{ currentDb.vendor }}</strong></div>
          <div style="word-break: break-all;">地址：{{ currentDb.jdbcUrl || '-' }}</div>
          <div v-if="currentDb.schema">Schema：{{ currentDb.schema }}</div>
        </div>
      </div>

      <el-divider style="margin: 16px 0;" />

      <div v-if="!showDbForm">
        <el-button @click="showDbForm = true">变更数据源</el-button>
      </div>

      <template v-else>
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          style="margin-bottom: 16px;"
          title="变更数据源需要重启系统后方可生效"
          description="新配置将通过连接测试后写入 application-local.yml。重启前系统仍使用当前数据源。"
        />
        <el-form label-position="top" style="max-width: 520px;">
          <el-form-item label="数据库类型" required>
            <el-select v-model="dbForm.vendor" style="width: 100%">
              <el-option v-if="auth.edition !== 'TEAM'" label="SQLite（内置文件库）" value="sqlite" />
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
            <el-button type="primary" :loading="dbSaving" :disabled="!dbTestOk" @click="saveDbConfig">
              保存配置
            </el-button>
            <el-button text @click="showDbForm = false">取消</el-button>
          </el-form-item>
        </el-form>
      </template>

      <el-alert
        v-if="dbRestartRequired"
        type="success"
        :closable="false"
        show-icon
        style="margin-top: 16px;"
        title="数据源配置已保存，重启后生效"
      />
    </el-card>

    <!-- Danger Zone -->
    <el-card class="danger-zone" shadow="never">
      <template #header>
        <div class="danger-zone-header">
          <el-icon color="var(--el-color-danger)" :size="20"><WarningFilled /></el-icon>
          <span class="danger-zone-title">危险区域</span>
        </div>
      </template>

      <div class="danger-zone-item">
        <div class="danger-zone-info">
          <div class="danger-zone-label">恢复出厂设置</div>
          <div class="danger-zone-desc">
            将系统恢复到首次部署时的初始状态。此操作将<strong>永久删除</strong>所有用户数据、系统配置和业务元数据。
            操作完成后，系统将重新进入初始化向导。此操作<strong>不可恢复</strong>。
          </div>
        </div>
        <el-button type="danger" plain @click="showConfirmDialog">
          恢复出厂设置
        </el-button>
      </div>
    </el-card>

    <!-- Database Migration -->
    <el-card style="margin-bottom: 20px;" shadow="never">
      <template #header>
        <div style="font-weight: 600; font-size: 16px; display: flex; align-items: center; gap: 8px;">
          <el-icon color="var(--el-color-primary)"><Switch /></el-icon>
          <span>数据迁移</span>
        </div>
      </template>

      <div style="font-size: 13px; color: var(--el-text-color-regular); line-height: 1.8; margin-bottom: 12px;">
        <div>将当前数据库的全部数据迁移到另一种数据库（如 SQLite → SQL Server / 达梦）。</div>
        <div style="margin-top: 4px;">
          流程：① 在当前系统导出数据（期间系统进入只读维护状态）→ ② 在"数据源"中配置目标数据库并重启 → ③ 在新系统中执行导入（自动校验行数）。
        </div>
      </div>

      <div v-if="migrationStatus" style="font-size: 13px; color: var(--el-text-color-regular); margin-bottom: 12px;">
        <el-tag v-if="migrationStatus.maintenanceMode" type="warning">维护模式开启中（写操作已锁定）</el-tag>
        <el-tag v-else type="success">系统正常</el-tag>
        <el-tag v-if="migrationStatus.exportFileExists" type="info" style="margin-left: 8px;">迁移文件已就绪</el-tag>
      </div>

      <div style="display: flex; gap: 12px;">
        <el-button type="primary" plain :loading="migrating" @click="doExport">① 导出数据</el-button>
        <el-button type="success" plain :loading="migrating" @click="doImport">③ 导入数据</el-button>
        <el-button v-if="migrationStatus?.maintenanceMode" type="danger" plain @click="doAbort">解除维护模式</el-button>
      </div>
    </el-card>

    <!-- Step 1: First confirmation dialog -->
    <el-dialog
      v-model="confirmVisible"
      title="⚠️ 恢复出厂设置"
      width="480px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-alert
        type="error"
        :closable="false"
        show-icon
        style="margin-bottom: 20px;"
      >
        <template #title>
          <strong>此操作将永久清除以下所有数据：</strong>
        </template>
        <template #default>
          <ul style="margin: 8px 0 0; padding-left: 18px; line-height: 1.8;">
            <li>所有用户账号（仅保留默认管理员 admin）</li>
            <li>所有注册机构、注册人员、命名空间配置</li>
            <li>所有数据元、数据类型、值域等元数据</li>
            <li>所有映射方案、分类体系</li>
            <li>所有系统设置（运行模式等）</li>
          </ul>
        </template>
      </el-alert>

      <p style="color: var(--el-text-color-regular); margin: 0;">
        确认继续？操作完成后，系统将回到未初始化状态并重新启动向导。
      </p>

      <template #footer>
        <el-button @click="confirmVisible = false">取消</el-button>
        <el-button type="danger" @click="showPasswordDialog">
          我已了解风险，继续
        </el-button>
      </template>
    </el-dialog>

    <!-- Step 2: Password verification dialog -->
    <el-dialog
      v-model="passwordVisible"
      title="🔐 身份验证"
      width="420px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <p style="margin-top: 0; color: var(--el-text-color-regular);">
        请输入您当前的登录密码以确认此操作：
      </p>
      <el-form @submit.prevent="executeReset">
        <el-form-item>
          <el-input
            v-model="password"
            type="password"
            name="password"
            autocomplete="current-password"
            placeholder="请输入管理员密码"
            show-password
            :disabled="resetting"
            @keyup.enter="executeReset"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="passwordVisible = false" :disabled="resetting">取消</el-button>
        <el-button
          type="danger"
          :loading="resetting"
          :disabled="!password.trim()"
          @click="executeReset"
        >
          {{ resetting ? '正在重置...' : '确认重置' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- Full-screen loading overlay during reset -->
    <Teleport to="body">
      <Transition name="fade">
        <div v-if="resetting" class="reset-overlay">
          <div class="reset-overlay-content">
            <el-icon class="reset-spinner" :size="48"><Loading /></el-icon>
            <h3>正在恢复出厂设置…</h3>
            <p>请勿关闭浏览器，系统将在完成后自动跳转。</p>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { WarningFilled, Loading, Setting, Coin, Switch } from '@element-plus/icons-vue';
import { systemApi } from '@/api/system';
import { useAuthStore } from '@/stores/auth';
import request from '@/utils/request';

const router = useRouter();
const auth = useAuthStore();

const confirmVisible = ref(false);
const passwordVisible = ref(false);
const password = ref('');
const resetting = ref(false);

const newUserRequireAudit = ref(false);
const allowUserRegistration = ref(true);
const loadingSettings = ref(false);

// ── Datasource settings ─────────────────────────────────────────
interface CurrentDb {
  vendor: string;
  jdbcUrl: string | null;
  schema: string | null;
}
const currentDb = ref<CurrentDb | null>(null);
const showDbForm = ref(false);
const dbForm = ref({
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
const dbTestOk = ref(false);
const dbRestartRequired = ref(false);

// SQLite 模式下凭据输入框只是隐藏不渲染，残留值（如浏览器自动填充）仍会随保存请求提交
watch(() => dbForm.value.vendor, () => {
  dbForm.value.username = '';
  dbForm.value.password = '';
  dbTestOk.value = false;
});

// ── Database migration ──────────────────────────────────────────
interface MigrationStatus {
  maintenanceMode: boolean;
  exportFileExists: boolean;
  exportFile: string;
  vendor: string;
}
const migrationStatus = ref<MigrationStatus | null>(null);
const migrating = ref(false);

async function loadMigrationStatus() {
  try {
    migrationStatus.value = await request.get<MigrationStatus>('/migration/status');
  } catch {
    migrationStatus.value = null;
  }
}

async function doExport() {
  migrating.value = true;
  try {
    const result = await request.post<{ tableCount: number; totalRows: number; file: string }>(
      '/migration/export'
    );
    ElMessage.success(`导出完成：${result.tableCount} 张表、${result.totalRows} 行。系统已进入只读维护状态。`);
    await loadMigrationStatus();
  } catch {
    // 拦截器已提示
  } finally {
    migrating.value = false;
  }
}

async function doImport() {
  migrating.value = true;
  try {
    const result = await request.post<{ tableCount: number; totalRows: number; verified: boolean }>(
      '/migration/import?force=true'
    );
    ElMessage.success(`导入完成：${result.tableCount} 张表、${result.totalRows} 行，行数校验通过。`);
    await loadMigrationStatus();
  } catch {
    // 拦截器已提示
  } finally {
    migrating.value = false;
  }
}

async function doAbort() {
  try {
    await request.post('/migration/abort');
    ElMessage.success('已解除只读维护状态');
    await loadMigrationStatus();
  } catch {
    // 拦截器已提示
  }
}

async function loadDbConfig() {
  try {
    currentDb.value = await request.get<CurrentDb>('/datasource/config');
  } catch {
    currentDb.value = null;
  }
}

async function testDbConnection() {
  dbTesting.value = true;
  dbTestOk.value = false;
  try {
    await request.post('/datasource/test-connection', {
      vendor: dbForm.value.vendor,
      host: dbForm.value.host,
      port: dbForm.value.port,
      database: dbForm.value.database,
      filePath: dbForm.value.filePath,
      username: dbForm.value.username || null,
      password: dbForm.value.password || null,
    });
    dbTestOk.value = true;
    ElMessage.success('连接测试成功');
  } catch {
    // 拦截器已提示失败原因
  } finally {
    dbTesting.value = false;
  }
}

async function saveDbConfig() {
  dbSaving.value = true;
  try {
    await request.put('/datasource/config', {
      vendor: dbForm.value.vendor,
      host: dbForm.value.host,
      port: dbForm.value.port,
      database: dbForm.value.database,
      filePath: dbForm.value.filePath,
      username: dbForm.value.username || null,
      password: dbForm.value.password || null,
    });
    dbRestartRequired.value = true;
    showDbForm.value = false;
    ElMessage.success('数据源配置已保存，重启后生效');
  } catch {
    // 拦截器已提示失败原因
  } finally {
    dbSaving.value = false;
  }
}

async function loadSettings() {
  loadingSettings.value = true;
  try {
    const [auditRes, regRes] = await Promise.all([
      systemApi.getNewUserRequireAudit(),
      systemApi.getAllowUserRegistration()
    ]);
    newUserRequireAudit.value = auditRes;
    allowUserRegistration.value = regRes;
  } catch (err) {
    ElMessage.error('获取系统配置失败');
  } finally {
    loadingSettings.value = false;
  }
}

async function handleAuditSettingChange(val: boolean | string | number) {
  loadingSettings.value = true;
  try {
    await systemApi.setNewUserRequireAudit(!!val);
    ElMessage.success('注册审核配置更新成功');
  } catch (err) {
    newUserRequireAudit.value = !val; // rollback
    ElMessage.error('更新注册审核配置失败');
  } finally {
    loadingSettings.value = false;
  }
}

async function handleAllowRegistrationChange(val: boolean | string | number) {
  loadingSettings.value = true;
  try {
    await systemApi.setAllowUserRegistration(!!val);
    ElMessage.success('注册配置更新成功');
  } catch (err) {
    allowUserRegistration.value = !val; // rollback
    ElMessage.error('更新注册配置失败');
  } finally {
    loadingSettings.value = false;
  }
}

onMounted(() => {
  loadSettings();
  loadDbConfig();
  loadMigrationStatus();
});

function showConfirmDialog() {
  confirmVisible.value = true;
}

function showPasswordDialog() {
  confirmVisible.value = false;
  password.value = '';
  passwordVisible.value = true;
}

async function executeReset() {
  if (!password.value.trim()) return;

  resetting.value = true;
  try {
    await systemApi.factoryReset(password.value);

    // ── Frontend state cleanup ────────────────────────────────────────────
    // We intentionally do NOT clear localStorage or fully log out: the
    // backend has wiped its data, so the next navigation will hit the
    // router's "system not initialized" guard (which checks /init-wizard/status
    // first) and redirect to /init-wizard — regardless of whether a stale
    // token is still present.
    //
    // What we DO need to do is:
    //   1. Mark the in-memory "initialized" cache as false so the next guard
    //      run doesn't trust a stale "true" cached before the reset.
    //   2. Drop the user profile (the cached account no longer exists in
    //      the database) so /auth/me isn't called with a stale identity.
    //   3. Clear any non-auth local state (UI prefs, etc.).
    auth.setInitialized(false);
    auth.user = null;
    localStorage.removeItem('language');
    localStorage.removeItem('theme');

    ElMessage.success('恢复出厂设置已完成，即将跳转到初始化向导…');

    // Short delay to show the message, then redirect
    setTimeout(() => {
      router.replace('/init-wizard');
      // Force full reload to clear any cached state
      window.location.href = '/init-wizard';
    }, 1500);
  } catch (err: any) {
    resetting.value = false;
    passwordVisible.value = false;
    // The error message is already shown by the request interceptor
  }
}
</script>

<style scoped>
.system-settings-page {
  max-width: 800px;
}

.danger-zone {
  border: 1px solid var(--el-color-danger-light-5);
}

.danger-zone :deep(.el-card__header) {
  background-color: var(--el-color-danger-light-9);
  border-bottom: 1px solid var(--el-color-danger-light-5);
  padding: 12px 20px;
}

.danger-zone-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.danger-zone-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-color-danger);
}

.danger-zone-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}

.danger-zone-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 4px;
}

.danger-zone-desc {
  font-size: 13px;
  color: var(--el-text-color-regular);
  line-height: 1.6;
}

/* Full-screen overlay */
.reset-overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.7);
  backdrop-filter: blur(4px);
}

.reset-overlay-content {
  text-align: center;
  color: #fff;
}

.reset-overlay-content h3 {
  margin: 16px 0 8px;
  font-size: 20px;
}

.reset-overlay-content p {
  margin: 0;
  font-size: 14px;
  opacity: 0.8;
}

.reset-spinner {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* Transition */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
