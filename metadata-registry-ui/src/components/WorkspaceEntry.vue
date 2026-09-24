<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRouter } from 'vue-router';
import { useWorkspaceBadge } from '@/composables/useWorkspaceBadge';

const { t } = useI18n();
const router = useRouter();
const { count, startPolling, stopPolling } = useWorkspaceBadge();

const tooltip = computed(() =>
  count.value > 0
    ? t('workspace.badgeTooltipWithCount', { count: count.value })
    : t('nav.workspace'),
);

function go() {
  router.push('/workspace');
}

onMounted(startPolling);
onBeforeUnmount(stopPolling);
</script>

<template>
  <el-tooltip :content="tooltip" placement="bottom">
    <el-badge
      :value="count"
      :hidden="count === 0"
      :max="99"
      data-testid="workspace-entry-badge"
    >
      <el-button text circle @click="go" data-testid="workspace-entry-btn">
        <el-icon :size="18"><Briefcase /></el-icon>
      </el-button>
    </el-badge>
  </el-tooltip>
</template>
