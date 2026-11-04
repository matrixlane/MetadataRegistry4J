<template>
  <div class="datasource-settings-page">
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
            <el-input v-model="dbForm.filePath" placeholder="~/.metadata-registry/data/metadata-registry.db" />
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

    <el-card shadow="never">
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
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue';
import { Coin, Switch } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { useAuthStore } from '@/stores/auth';
import request from '@/utils/request';

const auth = useAuthStore();

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
  filePath: '~/.metadata-registry/data/metadata-registry.db',
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

onMounted(() => {
  loadDbConfig();
  loadMigrationStatus();
});
</script>
