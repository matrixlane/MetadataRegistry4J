<template>
  <div class="app-layout">
    <header class="app-header">
      <div class="header-left">
        <el-dropdown
          v-if="activeRegistrar.active && activeRegistrar.identities.length > 1"
          trigger="click"
          data-testid="header-active-registrar-menu"
          @command="switchActiveRegistrar"
        >
          <el-tag
            type="success"
            effect="light"
            round
            style="cursor: pointer;"
            data-testid="header-active-registrar"
          >
            {{ activeRegistrar.active.registrationAuthorityName }}
          </el-tag>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item
                v-for="identity in activeRegistrar.identities"
                :key="identity.registrarId"
                :command="identity.registrarId"
                :data-testid="`registrar-option-${identity.registrarId}`"
              >
                <el-icon v-if="identity.registrarId === activeRegistrar.active?.registrarId" style="margin-right: 4px;">
                  <Check />
                </el-icon>
                {{ identity.registrationAuthorityName }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-tag
          v-else-if="activeRegistrar.active"
          type="success"
          effect="light"
          round
          data-testid="header-active-registrar"
        >
          {{ activeRegistrar.active.registrationAuthorityName }}
        </el-tag>
      </div>
      <div class="header-right" data-testid="header-right">
        <WorkspaceEntry />
        <AppSettingsEntry />
        <el-tag v-if="auth.user" type="primary" effect="light" round style="cursor: pointer;" @click="goToProfile" data-testid="header-user-tag">
          <el-icon><UserFilled /></el-icon>
          {{ auth.user.displayName || auth.user.username }}
        </el-tag>
        <!-- 个人版免登录：无登出概念 -->
        <el-button v-if="!auth.bypassLogin" text type="primary" @click="handleLogout" data-testid="header-logout-btn">
          <el-icon><SwitchButton /></el-icon>
          {{ $t('auth.logout') }}
        </el-button>
      </div>
    </header>
    <div class="app-body">
      <aside class="app-sidebar" :class="{ collapsed }">
        <div class="sidebar-header">
          <div class="sidebar-brand">
            <img src="/favicon.svg" width="36" height="36" alt="MDR" />
            <div v-if="!collapsed" class="brand-text-block">
              <span class="brand-text">{{ $t('app.name') }}</span>
              <span class="brand-subtext">Metadata Registry</span>
            </div>
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
            :default-active="activeMenu"
            router
            background-color="transparent"
          >
            <!-- 概览 -->
            <el-menu-item index="/dashboard">
              <el-icon><DataLine /></el-icon>
              <template #title>
                <span class="menu-label">
                  <span class="menu-label__main">{{ $t('nav.dashboard') }}</span>
                  <span class="menu-label__sub">Overview</span>
                </span>
              </template>
            </el-menu-item>

            <!-- 数据字典 -->
            <el-menu-item index="/data-dictionary">
              <el-icon><Reading /></el-icon>
              <template #title>
                <span class="menu-label">
                  <span class="menu-label__main">{{ $t('nav.dictionary') }}</span>
                  <span class="menu-label__sub">Data Dictionary</span>
                </span>
              </template>
            </el-menu-item>

            <!-- 概念模块 -->
            <el-sub-menu index="/concept-module-group">
              <template #title>
                <el-icon><SetUp /></el-icon>
                <span class="menu-label">
                  <span class="menu-label__main">{{ $t('nav.conceptModule') }}</span>
                  <span class="menu-label__sub">Concept</span>
                </span>
              </template>
              <el-menu-item index="/object-classes">
                <template #title>
                  <span class="menu-label">
                    <span class="menu-label__main">{{ $t('nav.objectClass') }}</span>
                    <span class="menu-label__sub">Object Class</span>
                  </span>
                </template>
              </el-menu-item>
              <el-menu-item index="/properties">
                <template #title>
                  <span class="menu-label">
                    <span class="menu-label__main">{{ $t('nav.property') }}</span>
                    <span class="menu-label__sub">Property</span>
                  </span>
                </template>
              </el-menu-item>
              <el-menu-item index="/data-element-concepts?group=concepts">
                <template #title>
                  <span class="menu-label">
                    <span class="menu-label__main">{{ $t('nav.dataElementConcept') }}</span>
                    <span class="menu-label__sub">Data Element Concept</span>
                  </span>
                </template>
              </el-menu-item>
              <el-menu-item index="/conceptual-domains?group=concepts">
                <template #title>
                  <span class="menu-label">
                    <span class="menu-label__main">{{ $t('nav.conceptualDomain') }}</span>
                    <span class="menu-label__sub">Conceptual Domain</span>
                  </span>
                </template>
              </el-menu-item>
              <el-menu-item index="/value-meanings">
                <template #title>
                  <span class="menu-label">
                    <span class="menu-label__main">{{ $t('nav.valueMeaning') }}</span>
                    <span class="menu-label__sub">Value Meaning</span>
                  </span>
                </template>
              </el-menu-item>
              <el-menu-item index="/dimensionality">
                <template #title>
                  <span class="menu-label">
                    <span class="menu-label__main">{{ $t('nav.dimensionality') }}</span>
                    <span class="menu-label__sub">Dimensionality</span>
                  </span>
                </template>
              </el-menu-item>
              <el-menu-item index="/measure-classes">
                <template #title>
                  <span class="menu-label">
                    <span class="menu-label__main">{{ $t('nav.measureClass') }}</span>
                    <span class="menu-label__sub">Measure Class</span>
                  </span>
                </template>
              </el-menu-item>
              <el-menu-item index="/units-of-measure">
                <template #title>
                  <span class="menu-label">
                    <span class="menu-label__main">{{ $t('nav.unitOfMeasure') }}</span>
                    <span class="menu-label__sub">Unit of Measure</span>
                  </span>
                </template>
              </el-menu-item>
            </el-sub-menu>

            <!-- 表示模块 -->
            <el-sub-menu index="/representation-module-group">
              <template #title>
                <el-icon><Document /></el-icon>
                <span class="menu-label">
                  <span class="menu-label__main">{{ $t('nav.representationModule') }}</span>
                  <span class="menu-label__sub">Representation</span>
                </span>
              </template>
              <el-menu-item index="/data-elements">
                <template #title>
                  <span class="menu-label">
                    <span class="menu-label__main">{{ $t('nav.dataElement') }}</span>
                    <span class="menu-label__sub">Data Element</span>
                  </span>
                </template>
              </el-menu-item>
              <el-menu-item index="/value-domains">
                <template #title>
                  <span class="menu-label">
                    <span class="menu-label__main">{{ $t('nav.valueDomain') }}</span>
                    <span class="menu-label__sub">Value Domain</span>
                  </span>
                </template>
              </el-menu-item>
              <el-menu-item index="/data-type-schemes">
                <template #title>
                  <span class="menu-label">
                    <span class="menu-label__main">{{ $t('nav.dataTypeScheme') }}</span>
                    <span class="menu-label__sub">Data Type Scheme</span>
                  </span>
                </template>
              </el-menu-item>
              <el-menu-item index="/data-types">
                <template #title>
                  <span class="menu-label">
                    <span class="menu-label__main">{{ $t('nav.dataType') }}</span>
                    <span class="menu-label__sub">Data Type</span>
                  </span>
                </template>
              </el-menu-item>
            </el-sub-menu>

            <!-- 基础与核心模块 -->
            <el-sub-menu index="/basic-core-module-group">
              <template #title>
                <el-icon><User /></el-icon>
                <span class="menu-label">
                  <span class="menu-label__main">{{ $t('nav.basicAndCoreModule') }}</span>
                  <span class="menu-label__sub">Basic &amp; Core</span>
                </span>
              </template>
              <el-menu-item index="/individuals">
                <template #title>
                  <span class="menu-label">
                    <span class="menu-label__main">{{ $t('nav.individual') }}</span>
                    <span class="menu-label__sub">Individual</span>
                  </span>
                </template>
              </el-menu-item>
              <el-menu-item index="/organizations">
                <template #title>
                  <span class="menu-label">
                    <span class="menu-label__main">{{ $t('nav.organization') }}</span>
                    <span class="menu-label__sub">Organization</span>
                  </span>
                </template>
              </el-menu-item>
              <el-menu-item index="/roles">
                <template #title>
                  <span class="menu-label">
                    <span class="menu-label__main">{{ $t('nav.role') }}</span>
                    <span class="menu-label__sub">Role</span>
                  </span>
                </template>
              </el-menu-item>
              <el-menu-item index="/contacts">
                <template #title>
                  <span class="menu-label">
                    <span class="menu-label__main">{{ $t('nav.contact') }}</span>
                    <span class="menu-label__sub">Contact</span>
                  </span>
                </template>
              </el-menu-item>
              <el-menu-item index="/document-types">
                <template #title>
                  <span class="menu-label">
                    <span class="menu-label__main">{{ $t('nav.documentType') }}</span>
                    <span class="menu-label__sub">Document Type</span>
                  </span>
                </template>
              </el-menu-item>
              <el-menu-item index="/languages">
                <template #title>
                  <span class="menu-label">
                    <span class="menu-label__main">{{ $t('nav.language') }}</span>
                    <span class="menu-label__sub">Language</span>
                  </span>
                </template>
              </el-menu-item>
              <el-menu-item index="/reference-documents">
                <template #title>
                  <span class="menu-label">
                    <span class="menu-label__main">{{ $t('nav.referenceDocument') }}</span>
                    <span class="menu-label__sub">Reference Document</span>
                  </span>
                </template>
              </el-menu-item>
              <el-menu-item index="/contexts">
                <template #title>
                  <span class="menu-label">
                    <span class="menu-label__main">{{ $t('nav.context') }}</span>
                    <span class="menu-label__sub">Context</span>
                  </span>
                </template>
              </el-menu-item>
            </el-sub-menu>

            <!-- 标识与命名模块 -->
            <el-sub-menu index="/identification-naming-group">
              <template #title>
                <el-icon><PriceTag /></el-icon>
                <span class="menu-label">
                  <span class="menu-label__main">{{ $t('nav.identificationAndNaming') }}</span>
                  <span class="menu-label__sub">Identification &amp; Naming</span>
                </span>
              </template>
              <el-menu-item index="/namespaces">
                <template #title>
                  <span class="menu-label">
                    <span class="menu-label__main">{{ $t('nav.namespace') }}</span>
                    <span class="menu-label__sub">Namespace</span>
                  </span>
                </template>
              </el-menu-item>
              <el-menu-item index="/naming-conventions">
                <template #title>
                  <span class="menu-label">
                    <span class="menu-label__main">{{ $t('nav.namingConvention') }}</span>
                    <span class="menu-label__sub">Naming Convention</span>
                  </span>
                </template>
              </el-menu-item>
            </el-sub-menu>

            <!-- 注册模块 -->
            <el-sub-menu index="/registration-module-group">
              <template #title>
                <el-icon><OfficeBuilding /></el-icon>
                <span class="menu-label">
                  <span class="menu-label__main">{{ $t('nav.registrationModule') }}</span>
                  <span class="menu-label__sub">Registration</span>
                </span>
              </template>
              <el-menu-item index="/registry-specifications">
                <template #title>
                  <span class="menu-label">
                    <span class="menu-label__main">{{ $t('nav.registrySpecification') }}</span>
                    <span class="menu-label__sub">Registry Specification</span>
                  </span>
                </template>
              </el-menu-item>
              <el-menu-item index="/registration-authorities">
                <template #title>
                  <span class="menu-label">
                    <span class="menu-label__main">{{ $t('nav.registrationAuthority') }}</span>
                    <span class="menu-label__sub">Registration Authority</span>
                  </span>
                </template>
              </el-menu-item>
              <el-menu-item index="/registrars">
                <template #title>
                  <span class="menu-label">
                    <span class="menu-label__main">{{ $t('nav.registrar') }}</span>
                    <span class="menu-label__sub">Registrar</span>
                  </span>
                </template>
              </el-menu-item>
              <el-menu-item index="/registration-process">
                <template #title>
                  <span class="menu-label">
                    <span class="menu-label__main">{{ $t('nav.registrationProcess') }}</span>
                    <span class="menu-label__sub">Registration Process</span>
                  </span>
                </template>
              </el-menu-item>
              <el-menu-item index="/administered-items">
                <template #title>
                  <span class="menu-label">
                    <span class="menu-label__main">{{ $t('nav.administeredItem') }}</span>
                    <span class="menu-label__sub">Administered Item</span>
                  </span>
                </template>
              </el-menu-item>
            </el-sub-menu>

            <!-- 分类模块 -->
            <el-sub-menu index="/classification-module-group">
              <template #title>
                <el-icon><CollectionTag /></el-icon>
                <span class="menu-label">
                  <span class="menu-label__main">{{ $t('nav.classificationModule') }}</span>
                  <span class="menu-label__sub">Classification</span>
                </span>
              </template>
              <el-menu-item index="/classification">
                <template #title>
                  <span class="menu-label">
                    <span class="menu-label__main">{{ $t('nav.classificationScheme') }}</span>
                    <span class="menu-label__sub">Classification Scheme</span>
                  </span>
                </template>
              </el-menu-item>
              <el-menu-item index="/classification-items">
                <template #title>
                  <span class="menu-label">
                    <span class="menu-label__main">{{ $t('nav.classificationItem') }}</span>
                    <span class="menu-label__sub">Classification Item</span>
                  </span>
                </template>
              </el-menu-item>
            </el-sub-menu>

            <!-- 项目映射模块 -->
            <el-sub-menu index="/item-mapping-module-group">
              <template #title>
                <el-icon><Connection /></el-icon>
                <span class="menu-label">
                  <span class="menu-label__main">{{ $t('nav.itemMappingModule') }}</span>
                  <span class="menu-label__sub">Item Mapping</span>
                </span>
              </template>
              <el-menu-item index="/item-mappings">
                <template #title>
                  <span class="menu-label">
                    <span class="menu-label__main">{{ $t('nav.itemMapping') }}</span>
                    <span class="menu-label__sub">Item Mapping</span>
                  </span>
                </template>
              </el-menu-item>
            </el-sub-menu>
          </el-menu>
        </el-scrollbar>

        <!-- 我的：固定于侧边栏底部 -->
        <div class="sidebar-footer" data-testid="sidebar-footer">
          <div class="sidebar-footer-item" data-testid="nav-my" @click="goToProfile">
            <el-icon><User /></el-icon>
            <span v-if="!collapsed" class="sidebar-footer-label">{{ $t('nav.my') }}</span>
          </div>
          <!-- 系统（仅 Admin）：位于"我的"之下；二级菜单在主内容区展示 -->
          <div v-if="auth.isAdmin" class="sidebar-footer-item" data-testid="nav-system" @click="goToSystem">
            <el-icon><Setting /></el-icon>
            <span v-if="!collapsed" class="sidebar-footer-label">{{ $t('nav.system') }}</span>
          </div>
        </div>
      </aside>
      <main class="app-main">
        <el-alert
          v-if="showDefaultLanguageAlert"
          :title="defaultLanguageAlertText"
          type="warning"
          :closable="false"
          show-icon
          style="margin-bottom: 12px;"
          data-testid="default-language-alert"
        />
        <router-view />
      </main>

    <!-- 首登完善联系人信息提示（改密弹窗优先级更高） -->
    <el-dialog
      :model-value="showProfilePromptDialog"
      :title="$t('profilePrompt.title')"
      :show-close="false"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      width="420px"
      data-testid="profile-prompt-dialog"
    >
      <p style="margin: 0 0 16px; font-size: 14px; line-height: 1.6;">
        {{ $t('profilePrompt.message') }}
      </p>
      <template #footer>
        <el-button data-testid="profile-prompt-skip" @click="dismissProfilePrompt">
          {{ $t('profilePrompt.skip') }}
        </el-button>
        <el-button type="primary" data-testid="profile-prompt-complete" @click="goToProfileFromPrompt">
          {{ $t('profilePrompt.complete') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 安全改密弹窗 -->
    <el-dialog
      :model-value="showChangePasswordDialog"
      title="安全提示：需要修改密码"
      :show-close="false"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      width="450px"
    >
      <el-form
        ref="pwdFormRef"
        :model="pwdForm"
        :rules="pwdRules"
        label-position="top"
      >
        <!-- Hidden username field so browser password managers associate the
             new password with the account instead of treating the password as
             the username. Must not be display:none or browsers ignore it. -->
        <input
          type="text"
          name="username"
          autocomplete="username"
          :value="auth.user?.username || ''"
          tabindex="-1"
          aria-hidden="true"
          style="position: absolute; opacity: 0; height: 0; width: 0; pointer-events: none;"
        />
        <p style="margin: 0 0 16px; color: #e6a23c; font-size: 14px; line-height: 1.5;">
          {{ auth.user?.emergencyLogin ? '您当前使用的是应急码登录，为了系统安全，请立即设置新密码。新密码不能与原密码相同。' : '您当前使用的是临时密码或系统初始凭证登录，请设置新密码以继续使用系统。' }}
        </p>
        <el-form-item
          v-if="requireOldPassword"
          label="原密码 (或应急码)"
          prop="oldPassword"
        >
          <el-input
            v-model="pwdForm.oldPassword"
            type="password"
            name="password"
            autocomplete="current-password"
            placeholder="请输入原密码或应急码"
            show-password
          />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="pwdForm.newPassword"
            type="password"
            name="newPassword"
            autocomplete="new-password"
            placeholder="8-64 个字符"
            show-password
          />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input
            v-model="pwdForm.confirmPassword"
            type="password"
            name="confirmPassword"
            autocomplete="new-password"
            placeholder="请再次输入新密码"
            show-password
          />
        </el-form-item>
        <el-form-item v-if="pwdError" style="margin-bottom: 0;">
          <el-alert
            :title="pwdError"
            type="error"
            :closable="false"
            show-icon
            effect="light"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button
          type="primary"
          :loading="submitLoading"
          @click="submitPasswordChange"
          style="width: 100%;"
        >
          确认修改并登录
        </el-button>
      </template>
    </el-dialog>

    <!-- AI 交互调试侧栏与浮动对话窗（仅 Registrar 业务角色渲染） -->
    <AiDebugDrawer v-if="auth.isRegistrarUser" v-model="showAiDebug" />
    <AiChatWindow v-if="auth.isRegistrarUser" />

    <!-- 全局划词「问 AI」入口 -->
    <el-button
      v-if="selectionButton"
      class="ai-ask-button"
      size="small"
      type="primary"
      round
      data-test="ai-ask-button"
      :style="{ left: `${selectionButton.x}px`, top: `${selectionButton.y}px` }"
      @mousedown.prevent
      @click="askAi"
    >✨ {{ $t('aiChat.askButton') }}</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted, onBeforeUnmount, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import { useAiChatStore } from '@/stores/aiChat';
import AiDebugDrawer from '@/components/assistant/AiDebugDrawer.vue';
import AiChatWindow from '@/components/assistant/AiChatWindow.vue';
import { assistantSettingsApi } from '@/api/assistant';
import { DEFAULT_HOTKEYS, parseHotkey, eventMatchesHotkey } from '@/utils/hotkey';
import type { ParsedHotkey } from '@/utils/hotkey';
import { Reading, Briefcase, Check } from '@element-plus/icons-vue';
import type { FormInstance } from 'element-plus';
import { ElMessage } from 'element-plus';
import request from '@/utils/request';
import { useActiveRegistrar } from '@/composables/useActiveRegistrar';
import { useRegistryDefaultLanguage } from '@/composables/useRegistryDefaultLanguage';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const {
  load: loadRegistryDefaultLanguage,
  defaultLanguageId,
  error: defaultLanguageError,
  loading: defaultLanguageLoading,
} = useRegistryDefaultLanguage();

const showDefaultLanguageAlert = computed(() =>
  !defaultLanguageLoading.value && (Boolean(defaultLanguageError.value) || (!!auth.user && !defaultLanguageId.value))
);

const defaultLanguageAlertText = computed(() => {
  if (defaultLanguageError.value) {
    return `注册表默认语言加载失败：${defaultLanguageError.value}，创建指称/定义时可能无法自动填充语言。`;
  }
  return '当前未配置注册表默认语言，创建指称/定义前请先在“注册规范”中设置默认语言。';
});

const collapsed = ref(false);

// ── Active Registrar 身份（浏览器端激活状态，useActiveRegistrar 单例；
//    切换 = 纯客户端动作：写 localStorage 并由请求头随请求传递）──
const activeRegistrar = useActiveRegistrar();

async function loadActiveRegistrar() {
  // 个人版 fetchMe 失败降级场景下 auth.user 为 null 也加载（bypass 免登录）
  if (!auth.user && !auth.bypassLogin) return;
  try {
    await activeRegistrar.load();
  } catch {
    // 加载失败保持空身份；错误展示由各消费方自行处理
  }
  // 通知依赖活动 Registrar 身份的对话框/页面重新拉取（default-namespace-by-active-registrar）
  window.dispatchEvent(new CustomEvent('mdregistry:active-registrar-changed'));
}

/** 点击顶栏机构标签切换激活身份；纯客户端动作，不发任何服务端请求。 */
function switchActiveRegistrar(registrarId: string) {
  activeRegistrar.switchRegistrar(registrarId);
}

onMounted(loadActiveRegistrar);
onMounted(loadRegistryDefaultLanguage);

// ── AI 交互调试侧栏（快捷键可配置，仅 Registrar 注册监听）────────────
const showAiDebug = ref(false);
const aiChat = useAiChatStore();

/** 调试侧栏开合绑定：默认 Ctrl+Shift+D，登录后按服务端设置覆盖 */
let aiDebugHotkey: ParsedHotkey | null = parseHotkey(DEFAULT_HOTKEYS['aiDebug.toggle']);

function onAiDebugKeydown(event: KeyboardEvent) {
  if (!aiDebugHotkey || !eventMatchesHotkey(event, aiDebugHotkey)) return;
  event.preventDefault();
  showAiDebug.value = !showAiDebug.value;
}

async function loadAiAssistSettings() {
  if (!auth.isRegistrarUser) return;
  try {
    const settings = await assistantSettingsApi.get();
    aiDebugHotkey =
      parseHotkey(settings.hotkeys?.['aiDebug.toggle']) ??
      parseHotkey(DEFAULT_HOTKEYS['aiDebug.toggle']);
  } catch {
    // 读取失败沿用默认绑定
  }
}

// ── 全局划词「问 AI」（排除输入控件，选区 ≥2 字符才浮现）────────────
const selectionButton = ref<{
  x: number;
  y: number;
  text: string;
  surroundingText?: string;
} | null>(null);

function isEditableTarget(el: Element | null | undefined): boolean {
  return !!el?.closest('input, textarea, [contenteditable]');
}

function onGlobalMouseup(event: MouseEvent) {
  // 等浏览器提交选区后再读取
  setTimeout(() => {
    const target = event.target as HTMLElement | null;
    if (isEditableTarget(target)) {
      selectionButton.value = null;
      return;
    }
    const selection = window.getSelection();
    const text = selection?.toString().trim() ?? '';
    if (!selection || selection.isCollapsed || text.length < 2) {
      selectionButton.value = null;
      return;
    }
    const anchorNode = selection.anchorNode;
    const anchorEl = anchorNode instanceof Element ? anchorNode : anchorNode?.parentElement;
    if (isEditableTarget(anchorEl)) {
      selectionButton.value = null;
      return;
    }
    const rect = selection.getRangeAt(0).getBoundingClientRect();
    selectionButton.value = {
      x: Math.max(8, rect.left + rect.width / 2),
      y: rect.bottom + 8,
      text: text.slice(0, 2000),
      surroundingText: (anchorEl?.textContent ?? '').slice(0, 2000),
    };
  }, 0);
}

function onGlobalMousedown(event: MouseEvent) {
  const el = event.target as HTMLElement | null;
  if (el?.closest('[data-test="ai-ask-button"]')) return;
  selectionButton.value = null;
}

function askAi() {
  if (!selectionButton.value) return;
  aiChat.openWithQuote({
    text: selectionButton.value.text,
    surroundingText: selectionButton.value.surroundingText,
  });
  selectionButton.value = null;
}

if (auth.isRegistrarUser) {
  window.addEventListener('keydown', onAiDebugKeydown);
  window.addEventListener('mouseup', onGlobalMouseup);
  window.addEventListener('mousedown', onGlobalMousedown);
  onMounted(loadAiAssistSettings);
  onBeforeUnmount(() => {
    window.removeEventListener('keydown', onAiDebugKeydown);
    window.removeEventListener('mouseup', onGlobalMouseup);
    window.removeEventListener('mousedown', onGlobalMousedown);
  });
}

const activeMenu = computed(() => {
  if (route.query.group) {
    return `${route.path}?group=${route.query.group}`;
  }
  if (route.path === '/conceptual-domains') {
    return '/conceptual-domains?group=specification';
  }
  if (route.path === '/data-element-concepts') {
    return '/data-element-concepts?group=specification';
  }
  return route.path;
});

function handleLogout() {
  // 重置模块级单例，避免下一账号脏读上一账号的身份/激活选择
  activeRegistrar.reset();
  auth.logout();
  // Use replace to prevent browser back from showing cached authenticated pages
  router.replace('/login');
}

function goToProfile() {
  router.push('/my-profile');
}

function goToSystem() {
  router.push('/system');
}

// ── Password Change Dialog for forceChangePassword ─────────────────
const showChangePasswordDialog = computed(() => !!auth.user?.forceChangePassword);

// ── First-login profile prompt (contact-identity-management) ────────
function profilePromptStorageKey(userId: string): string {
  return `profilePromptDismissed:${userId}`;
}

const profilePromptDismissed = ref(false);

function refreshProfilePromptDismissed() {
  if (!auth.user?.id) {
    profilePromptDismissed.value = false;
    return;
  }
  try {
    profilePromptDismissed.value = localStorage.getItem(profilePromptStorageKey(auth.user.id)) === 'true';
  } catch {
    profilePromptDismissed.value = false;
  }
}

watch(() => auth.user?.id, refreshProfilePromptDismissed, { immediate: true });

const showProfilePromptDialog = computed(() =>
  !!auth.user &&
  auth.user.profilePrompt === true &&
  !auth.user.forceChangePassword &&
  !profilePromptDismissed.value
);

function dismissProfilePrompt() {
  if (auth.user?.id) {
    try {
      localStorage.setItem(profilePromptStorageKey(auth.user.id), 'true');
    } catch {
      // 隐私模式或 localStorage 被禁用时仅回退到内存标记
    }
  }
  profilePromptDismissed.value = true;
}

function goToProfileFromPrompt() {
  dismissProfilePrompt();
  router.push('/my-profile');
}

const isFirstLoginAdmin = computed(() => {
  return auth.user?.username === 'admin' &&
    !auth.user?.emergencyLogin &&
    !auth.user?.lastLoginAt;
});

const requireOldPassword = computed(() => {
  return !isFirstLoginAdmin.value &&
    !auth.user?.emergencyLogin &&
    !auth.user?.tempPasswordUsed;
});

const pwdFormRef = ref<FormInstance>();
const submitLoading = ref(false);
const pwdError = ref('');

const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
});

const validateConfirmPassword = (rule: any, value: any, callback: any) => {
  if (value !== pwdForm.newPassword) {
    callback(new Error('两次输入密码不一致'));
  } else {
    callback();
  }
};

const pwdRules = computed(() => {
  const rules: any = {
    newPassword: [
      { required: true, message: '请输入新密码', trigger: 'blur' },
      { min: 8, max: 64, message: '密码长度需在 8-64 位之间', trigger: 'blur' }
    ],
    confirmPassword: [
      { required: true, message: '请再次输入新密码', trigger: 'blur' },
      { validator: validateConfirmPassword, trigger: 'blur' }
    ]
  };
  if (requireOldPassword.value) {
    rules.oldPassword = [
      { required: true, message: '请输入原密码或应急码', trigger: 'blur' }
    ];
  }
  return rules;
});

async function submitPasswordChange() {
  if (!pwdFormRef.value) return;
  await pwdFormRef.value.validate(async (valid) => {
    if (valid) {
      submitLoading.value = true;
      pwdError.value = '';
      try {
        const payload = {
          oldPassword: isFirstLoginAdmin.value ? '' : pwdForm.oldPassword,
          newPassword: pwdForm.newPassword
        };
        await request.post('/auth/change-password', payload);
        const username = auth.user?.username || 'admin';
        await auth.login({ username, password: pwdForm.newPassword });
        // Store/update credentials in the browser password manager so the
        // username is saved together with the new password (same approach as
        // the login page; Chrome/Edge support PasswordCredential).
        try {
          const PasswordCredential = (window as any).PasswordCredential;
          if (PasswordCredential && navigator.credentials) {
            await navigator.credentials.store(
              new PasswordCredential({
                id: username,
                password: pwdForm.newPassword,
                name: username,
              })
            );
          }
        } catch {
          // Ignore credential storage errors; the password change itself succeeded.
        }
        ElMessage.success('密码修改成功，已自动为您登录系统。');
        pwdForm.oldPassword = '';
        pwdForm.newPassword = '';
        pwdForm.confirmPassword = '';
      } catch (err: any) {
        // Show the rejection reason inside the dialog itself; the global
        // toast is easy to miss behind the modal mask.
        pwdError.value =
          err?.response?.data?.message ||
          err?.message ||
          '密码修改失败，请稍后重试';
      } finally {
        submitLoading.value = false;
      }
    }
  });
}
</script>

<style scoped lang="scss">
.highlight {
  font-weight: 600;
}

.ai-ask-button {
  position: fixed;
  z-index: 2500;
  transform: translateX(-50%);
}
</style>
