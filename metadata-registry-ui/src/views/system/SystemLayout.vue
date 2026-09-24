<template>
  <!-- 用户管理列表需宽度自适应；其余设置页保持原有宽度 -->
  <div class="system-layout" :class="{ 'is-users-view': isUsersView }">
    <h2 class="system-title" data-testid="system-title">{{ $t('nav.system') }}</h2>
    <div class="system-body">
      <!-- 一级平面菜单：通用 / 用户管理 / 数据源 / 危险区域 -->
      <el-menu class="system-nav" :default-active="activeMenu" router>
        <el-menu-item index="/system/general" data-testid="system-nav-general">
          {{ $t('systemSettings.nav.general') }}
        </el-menu-item>
        <el-menu-item index="/system/users" data-testid="system-nav-users">
          {{ $t('nav.userManagement') }}
        </el-menu-item>
        <el-menu-item index="/system/datasource" data-testid="system-nav-datasource">
          {{ $t('systemSettings.nav.datasource') }}
        </el-menu-item>
        <el-menu-item index="/system/danger" data-testid="system-nav-danger">
          {{ $t('systemSettings.nav.danger') }}
        </el-menu-item>
      </el-menu>
      <div class="system-content">
        <router-view />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';

const route = useRoute();

const activeMenu = computed(() => route.path);
const isUsersView = computed(() => route.path.startsWith('/system/users'));
</script>

<style scoped>
.system-layout {
  max-width: 1100px;
}

/* 用户管理列表：宽度自适应撑满主内容区 */
.system-layout.is-users-view {
  max-width: none;
  width: 100%;
}

.system-title {
  margin: 0 0 16px;
  font-size: 18px;
  font-weight: 600;
}

.system-body {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}

.system-nav {
  width: 160px;
  flex-shrink: 0;
  border-right: none;
}

.system-content {
  flex: 1;
  min-width: 0;
}
</style>
