<script setup lang="ts">
import { useI18n } from 'vue-i18n';
import AiAssistSettingsPage from '@/views/system/settings/AiAssistSettingsPage.vue';

const { t } = useI18n();
</script>

<template>
  <div class="app-settings-view">
    <h2 class="app-settings-title" data-testid="app-settings-title">
      <el-icon color="var(--el-color-primary)"><MagicStick /></el-icon>
      {{ t('nav.appSettings') }}
    </h2>
    <AiAssistSettingsPage />
  </div>
</template>

<style scoped>
.app-settings-view {
  padding: 4px 0 24px;
}

.app-settings-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 600;
  margin: 0 0 16px;
}
</style>
