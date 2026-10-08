<template>
  <div class="danger-settings-page">
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

    <!-- Step 2: Credential verification dialog (edition-differentiated) -->
    <el-dialog
      v-model="verifyVisible"
      :title="isPersonal ? '⚠️ 二次确认' : '🔐 身份验证'"
      width="420px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <!-- 个人版（PERSONAL）：无密码概念，输入固定确认短语（GitHub 删库式） -->
      <template v-if="isPersonal">
        <p style="margin-top: 0; color: var(--el-text-color-regular);">
          此操作不可恢复。请输入 <strong>「{{ CONFIRM_PHRASE }}」</strong> 以确认：
        </p>
        <el-form @submit.prevent="executeReset">
          <el-form-item>
            <el-input
              v-model="phraseInput"
              type="text"
              autocomplete="off"
              :placeholder="`请输入：${CONFIRM_PHRASE}`"
              :disabled="resetting"
              @keyup.enter="executeReset"
            />
          </el-form-item>
        </el-form>
      </template>

      <!-- 团队版（TEAM）：输入当前管理员密码 -->
      <template v-else>
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
      </template>

      <template #footer>
        <el-button @click="verifyVisible = false" :disabled="resetting">取消</el-button>
        <el-button
          type="danger"
          :loading="resetting"
          :disabled="!canSubmit"
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
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { WarningFilled, Loading } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { systemApi } from '@/api/system';
import { useAuthStore } from '@/stores/auth';

const router = useRouter();
const auth = useAuthStore();

/** 个人版二次确认的固定短语（与后端 FactoryResetService.CONFIRMATION_PHRASE 同步）。 */
const CONFIRM_PHRASE = '恢复出厂设置';

const confirmVisible = ref(false);
const verifyVisible = ref(false);
const password = ref('');
const phraseInput = ref('');
const resetting = ref(false);

/**
 * 部署版本：个人版渲染确认短语对话框，团队版渲染密码对话框。
 * 版本经 auth store（checkInitialized 写回 edition）单点解析；仍未知按团队版渲染
 * （fail-closed：个人版误显密码框后果是功能不可用，团队版误显短语框则削弱确认强度）。
 */
const edition = computed(() => auth.edition);
const isPersonal = computed(() => edition.value === 'PERSONAL');

const canSubmit = computed(() =>
  isPersonal.value ? phraseInput.value.trim() === CONFIRM_PHRASE : !!password.value.trim()
);

/** 版本未决时向 store 补全；失败保持 null（调用方按团队版兜底）。 */
async function resolveEdition() {
  if (auth.edition) return;
  try {
    await auth.checkInitialized(true);
  } catch {
    // 保持 null → 按团队版渲染
  }
}

onMounted(resolveEdition);

function showConfirmDialog() {
  confirmVisible.value = true;
}

async function showPasswordDialog() {
  confirmVisible.value = false;
  // 版本未决时先补全再开第二步对话框，避免密码框/短语框中途切换丢掉已输入内容
  await resolveEdition();
  password.value = '';
  phraseInput.value = '';
  verifyVisible.value = true;
}

async function executeReset() {
  if (!canSubmit.value) return;

  resetting.value = true;
  try {
    await systemApi.factoryReset(
      isPersonal.value
        ? { confirmationPhrase: phraseInput.value.trim() }
        : { password: password.value }
    );

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
    //   2. Drop the user profile (the cached account no longer exists in the
    //      database) so /auth/me isn't called with a stale identity.
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
    // 出错即关闭第二步对话框并丢弃已输入内容（含刻意选择：重试需从第一步重来，
    // 对破坏性操作偏保守；勿当 bug "修复"为重置失败保留凭据输入）。
    verifyVisible.value = false;
    // The error message is already shown by the request interceptor
  }
}
</script>

<style scoped>
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
