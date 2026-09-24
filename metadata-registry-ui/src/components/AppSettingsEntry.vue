<script setup lang="ts">
import { useI18n } from 'vue-i18n';
import { useRouter } from 'vue-router';

const { t } = useI18n();
const router = useRouter();

function go() {
  router.push('/app-settings');
}
</script>

<template>
  <el-tooltip :content="t('nav.appSettings')" placement="bottom">
    <el-button text circle @click="go" data-testid="app-settings-entry-btn">
      <el-icon :size="18"><MagicStick /></el-icon>
    </el-button>
  </el-tooltip>
</template>
