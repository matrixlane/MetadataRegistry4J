<template>
  <div class="app-layout">
    <header class="app-header">
      <div class="header-left">
        <img src="/favicon.svg" alt="logo" width="32" height="32" />
        <span class="header-title">{{ $t('app.name') }}</span>
      </div>
      <div class="header-right">
        <el-tag v-if="auth.user?.role" type="primary" size="small" effect="light" round>
          <el-icon><UserFilled /></el-icon>
          {{ auth.user.displayName || auth.user.username }}
        </el-tag>
        <el-button text type="primary" size="small" @click="handleLogout">
          <el-icon><SwitchButton /></el-icon>
          {{ $t('auth.logout') }}
        </el-button>
      </div>
    </header>
    <div class="app-body">
      <aside class="app-sidebar" :class="{ collapsed }">
        <div class="sidebar-header">
          <div class="sidebar-brand">
            <img src="/favicon.svg" width="32" height="32" alt="MDR" />
            <span v-if="!collapsed" class="brand-text">MDR</span>
          </div>
          <el-button
            v-if="!collapsed"
            text
            circle
            size="small"
            class="collapse-btn"
            @click="collapsed = true"
          >
            <el-icon><Fold /></el-icon>
          </el-button>
          <el-button
            v-else
            text
            circle
            size="small"
            class="collapse-btn"
            @click="collapsed = false"
          >
            <el-icon><Expand /></el-icon>
          </el-button>
        </div>
        <el-scrollbar>
          <el-menu
            :collapse="collapsed"
            :default-active="route.path"
            router
            background-color="transparent"
          >
            <el-menu-item index="/dashboard">
              <el-icon><DataLine /></el-icon>
              <template #title>{{ $t('nav.dashboard') }}</template>
            </el-menu-item>

            <!-- 数据字典（WI-015a） -->
            <el-menu-item index="/data-dictionary">
              <el-icon><Reading /></el-icon>
              <template #title>{{ $t('nav.dictionary') }}</template>
            </el-menu-item>

            <el-sub-menu index="/registration">
              <template #title>
                <el-icon><OfficeBuilding /></el-icon>
                <span>{{ $t('nav.registration') }}</span>
              </template>
              <el-menu-item index="/contexts">{{ $t('nav.context') }}</el-menu-item>
              <el-menu-item index="/registration-authorities">{{ $t('nav.registrationAuthority') }}</el-menu-item>
              <el-menu-item index="/registrars">{{ $t('nav.registrar') }}</el-menu-item>
              <el-menu-item index="/namespaces">{{ $t('nav.namespace') }}</el-menu-item>
            </el-sub-menu>

            <el-sub-menu index="/party">
              <template #title>
                <el-icon><User /></el-icon>
                <span>{{ $t('nav.party') }}</span>
              </template>
              <el-menu-item index="/organizations">{{ $t('nav.organization') }}</el-menu-item>
              <el-menu-item index="/individuals">{{ $t('nav.individual') }}</el-menu-item>
              <el-menu-item index="/contacts">{{ $t('nav.contact') }}</el-menu-item>
              <el-menu-item index="/reference-documents">{{ $t('nav.referenceDocument') }}</el-menu-item>
            </el-sub-menu>

            <el-sub-menu index="/concept">
              <template #title>
                <el-icon><SetUp /></el-icon>
                <span>{{ $t('nav.concept') }}</span>
              </template>
              <el-menu-item index="/object-classes">{{ $t('nav.objectClass') }}</el-menu-item>
              <el-menu-item index="/properties">{{ $t('nav.property') }}</el-menu-item>
              <el-menu-item index="/data-element-concepts">{{ $t('nav.dataElementConcept') }}</el-menu-item>
            </el-sub-menu>

            <el-sub-menu index="/classification">
              <template #title>
                <el-icon><Collection /></el-icon>
                <span>{{ $t('nav.classification') }}</span>
              </template>
              <el-menu-item index="/classification">{{ $t('nav.classification') }}</el-menu-item>
            </el-sub-menu>

            <!-- 映射网络（WI-011-2-1） -->
            <el-menu-item index="/mapping-network">
              <el-icon><Share /></el-icon>
              <template #title>{{ $t('nav.mappingNetwork') }}</template>
            </el-menu-item>

            <el-sub-menu index="/domain">
              <template #title>
                <el-icon><Grid /></el-icon>
                <span>{{ $t('nav.domain') }}</span>
              </template>
              <el-menu-item index="/conceptual-domains">{{ $t('nav.conceptualDomain') }}</el-menu-item>
              <el-menu-item index="/value-domains">{{ $t('nav.valueDomain') }}</el-menu-item>
              <el-menu-item index="/data-types">{{ $t('nav.dataType') }}</el-menu-item>
              <el-menu-item index="/data-type-schemes">{{ $t('nav.dataTypeScheme') }}</el-menu-item>
              <el-menu-item index="/units-of-measure">{{ $t('nav.unitOfMeasure') }}</el-menu-item>
              <el-menu-item index="/measure-classes">{{ $t('nav.measureClass') }}</el-menu-item>
            </el-sub-menu>

            <el-sub-menu index="/items">
              <template #title>
                <el-icon><Document /></el-icon>
                <span>{{ $t('nav.administeredItems') }}</span>
              </template>
              <el-menu-item index="/data-elements">{{ $t('nav.dataElement') }}</el-menu-item>
              <el-menu-item index="/value-domains">{{ $t('nav.valueDomain') }}</el-menu-item>
              <el-menu-item index="/data-element-concepts">{{ $t('nav.dataElementConcept') }}</el-menu-item>
              <el-menu-item index="/conceptual-domains">{{ $t('nav.conceptualDomain') }}</el-menu-item>
            </el-sub-menu>

            <el-sub-menu index="/system" v-if="auth.isAdmin">
              <template #title>
                <el-icon><Setting /></el-icon>
                <span>{{ $t('nav.system') }}</span>
              </template>
              <el-menu-item index="/app-users">{{ $t('nav.userManagement') }}</el-menu-item>
            </el-sub-menu>
          </el-menu>
        </el-scrollbar>
      </aside>
      <main class="app-main">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import { Reading } from '@element-plus/icons-vue';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();

const collapsed = ref(false);

function handleLogout() {
  auth.logout();
  router.push('/login');
}
</script>

<style scoped lang="scss">
.highlight {
  font-weight: 600;
}
</style>
