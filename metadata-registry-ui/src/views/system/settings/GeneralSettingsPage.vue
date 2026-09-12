<template>
  <div class="general-settings-page">
    <el-card shadow="never">
      <template #header>
        <div style="font-weight: 600; font-size: 16px; display: flex; align-items: center; gap: 8px;">
          <el-icon color="var(--el-color-primary)"><Setting /></el-icon>
          <span>{{ $t('systemSettings.nav.general') }}</span>
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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { Setting } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { systemApi } from '@/api/system';

const newUserRequireAudit = ref(false);
const allowUserRegistration = ref(true);
const loadingSettings = ref(false);

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

onMounted(loadSettings);
</script>
