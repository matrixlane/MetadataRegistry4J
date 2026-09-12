<template>
  <div class="system-settings-layout">
    <h2 style="margin-top: 0;">系统设置</h2>
    <div class="settings-body">
      <el-menu
        class="settings-nav"
        :default-active="activeMenu"
        router
      >
        <el-menu-item index="/system-settings/general">{{ $t('systemSettings.nav.general') }}</el-menu-item>
        <el-menu-item index="/system-settings/datasource">{{ $t('systemSettings.nav.datasource') }}</el-menu-item>
        <el-menu-item index="/system-settings/ai-assist">{{ $t('systemSettings.nav.aiAssist') }}</el-menu-item>
        <el-menu-item index="/system-settings/danger">{{ $t('systemSettings.nav.danger') }}</el-menu-item>
      </el-menu>
      <div class="settings-content">
        <router-view />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';

const route = useRoute();

const activeMenu = computed(() => {
  if (route.path.startsWith('/system-settings/')) return route.path;
  return '/system-settings/general';
});
</script>

<style scoped>
.system-settings-layout {
  max-width: 960px;
}
.settings-body {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}
.settings-nav {
  width: 160px;
  flex-shrink: 0;
  border-right: none;
}
.settings-content {
  flex: 1;
  min-width: 0;
}
</style>
