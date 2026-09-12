<template>
  <div class="ai-assist-settings-page">
    <!-- Agent 管理（既有卡片迁入；SUPER_ADMIN 可编辑） -->
    <el-card style="margin-bottom: 20px;" shadow="never">
      <template #header>
        <div style="font-weight: 600; font-size: 16px; display: flex; align-items: center; gap: 8px;">
          <el-icon color="var(--el-color-primary)"><MagicStick /></el-icon>
          <span>{{ t('systemInference.cardTitle') }}</span>
        </div>
      </template>

      <div style="font-size: 13px; color: var(--el-text-color-regular); line-height: 1.8; margin-bottom: 12px;">
        {{ t('systemInference.cardDesc') }}
      </div>

      <el-alert
        v-if="!canManageAgents"
        type="info"
        :closable="false"
        :title="t('systemInference.superAdminOnly')"
        style="margin-bottom: 12px;"
      />

      <template v-else>
        <el-table v-loading="agentsLoading" :data="agentRows" size="small" style="margin-bottom: 12px;">
          <el-table-column :label="t('systemInference.colName')" min-width="150">
            <template #default="{ row }">
              <div>{{ row.displayName }}</div>
              <el-tag size="small" type="info" effect="plain">{{ row.id }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('systemInference.colCommand')" min-width="200">
            <template #default="{ row }">
              <code class="agent-command">{{ row.command || t('systemInference.builtinDefault') }}</code>
              <div v-if="row.args.length" class="agent-args">{{ row.args.join(' ') }}</div>
            </template>
          </el-table-column>
          <el-table-column :label="t('systemInference.colStatus')" width="160">
            <template #default="{ row }">
              <el-tooltip :disabled="!row.detail" :content="row.detail" placement="top">
                <el-tag :type="row.available ? 'success' : 'danger'" size="small" data-test="agent-status">
                  {{ row.available ? t('systemInference.available') : t('systemInference.unavailable') }}
                </el-tag>
              </el-tooltip>
              <div v-if="row.available && row.detail" class="agent-detail">{{ row.detail }}</div>
            </template>
          </el-table-column>
          <el-table-column :label="t('systemInference.colEnabled')" width="80" align="center">
            <template #default="{ row }">
              <el-switch
                v-model="row.enabled"
                data-test="agent-enabled-switch"
                @change="(v: string | number | boolean) => onAgentEnabledChange(row, v)"
              />
            </template>
          </el-table-column>
          <el-table-column :label="t('systemInference.colDefault')" width="80" align="center">
            <template #default="{ row }">
              <el-radio
                :model-value="agentsDefault ?? undefined"
                :value="row.id"
                :disabled="!row.enabled"
                data-test="agent-default-radio"
                @change="agentsDefault = (row.id as string)"
              >
                <span class="sr-only">{{ row.id }}</span>
              </el-radio>
            </template>
          </el-table-column>
          <el-table-column :label="t('systemInference.colActions')" width="130" align="center">
            <template #default="{ row }">
              <el-button
                size="small"
                text
                type="primary"
                data-test="agent-edit"
                @click="openAgentDialog('edit', row)"
              >
                {{ t('systemInference.edit') }}
              </el-button>
              <el-button
                v-if="!row.builtin"
                size="small"
                text
                type="danger"
                data-test="agent-delete"
                @click="removeAgent(row)"
              >
                {{ t('systemInference.delete') }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <div style="display: flex; gap: 12px;">
          <el-button :loading="agentsProbing" data-test="agents-probe" @click="probeAgents">
            {{ t('systemInference.probe') }}
          </el-button>
          <el-button data-test="agents-add" @click="openAgentDialog('add')">
            {{ t('systemInference.addCustom') }}
          </el-button>
          <el-button type="primary" :loading="agentsSaving" data-test="agents-save" @click="saveAgents">
            {{ t('systemInference.save') }}
          </el-button>
        </div>
      </template>
    </el-card>

    <!-- Custom / builtin Agent edit dialog -->
    <el-dialog
      v-model="agentDialogVisible"
      :title="agentFormMode === 'add' ? t('systemInference.addCustom') : t('systemInference.editAgent')"
      width="480px"
      :close-on-click-modal="false"
      append-to-body
    >
      <el-form label-width="90px">
        <el-form-item :label="t('systemInference.fieldId')" required>
          <el-input
            v-model="agentForm.id"
            :disabled="agentFormMode === 'edit'"
            data-test="agent-form-id"
            placeholder="my-agent"
          />
        </el-form-item>
        <el-form-item :label="t('systemInference.fieldName')">
          <el-input
            v-model="agentForm.displayName"
            :disabled="agentFormEditingBuiltin"
            data-test="agent-form-name"
          />
        </el-form-item>
        <el-form-item :label="t('systemInference.fieldCommand')" required>
          <el-input
            v-model="agentForm.command"
            data-test="agent-form-command"
            placeholder="claude-agent-acp"
          />
        </el-form-item>
        <el-form-item :label="t('systemInference.fieldArgs')">
          <el-input
            v-model="agentForm.argsText"
            type="textarea"
            :rows="3"
            data-test="agent-form-args"
            :placeholder="t('systemInference.argsPlaceholder')"
          />
        </el-form-item>
      </el-form>
      <el-alert
        v-if="agentFormError"
        :title="agentFormError"
        type="error"
        :closable="false"
        data-test="agent-form-error"
        style="margin-top: 8px;"
      />
      <template #footer>
        <el-button @click="agentDialogVisible = false">{{ t('systemInference.cancel') }}</el-button>
        <el-button type="primary" data-test="agent-form-ok" @click="confirmAgentDialog">
          {{ t('systemInference.confirm') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 生成偏好 -->
    <el-card style="margin-bottom: 20px;" shadow="never">
      <template #header>
        <div style="font-weight: 600; font-size: 16px; display: flex; align-items: center; gap: 8px;">
          <el-icon color="var(--el-color-primary)"><Tickets /></el-icon>
          <span>{{ t('aiAssistPrefs.cardTitle') }}</span>
        </div>
      </template>
      <div class="prefs-row">
        <div>
          <div style="font-size: 14px; font-weight: 600; margin-bottom: 4px;">{{ t('aiAssistPrefs.candidateCount') }}</div>
          <div style="font-size: 13px; color: var(--el-text-color-regular);">
            {{ t('aiAssistPrefs.candidateCountHint') }}
          </div>
        </div>
        <el-input-number
          v-model="candidateCount"
          :min="1"
          :max="5"
          data-test="pref-candidate-count"
        />
      </div>
      <el-alert
        v-if="settingsLoadFailed"
        type="warning"
        :closable="false"
        :title="t('aiAssistPrefs.loadFailed')"
        style="margin-top: 12px;"
        data-test="prefs-load-failed"
      />
    </el-card>

    <!-- 快捷键 -->
    <el-card shadow="never">
      <template #header>
        <div style="font-weight: 600; font-size: 16px; display: flex; align-items: center; gap: 8px;">
          <el-icon color="var(--el-color-primary)"><Key /></el-icon>
          <span>{{ t('aiAssistHotkeys.cardTitle') }}</span>
        </div>
      </template>

      <el-table :data="hotkeyRows" size="small" style="margin-bottom: 12px;" data-test="hotkey-table">
        <el-table-column :label="t('aiAssistHotkeys.colCommand')" min-width="160">
          <template #default="{ row }">
            <code class="agent-command">{{ row.id }}</code>
          </template>
        </el-table-column>
        <el-table-column :label="t('aiAssistHotkeys.colDescription')" min-width="220">
          <template #default="{ row }">
            {{ t(row.descriptionKey) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('aiAssistHotkeys.colBinding')" min-width="180">
          <template #default="{ row }">
            <div
              v-if="recordingCommand === row.id"
              class="hotkey-capture"
              data-test="hotkey-capturing"
            >
              {{ t('aiAssistHotkeys.capturing') }}
            </div>
            <el-tag v-else size="small" effect="plain" data-test="hotkey-binding">{{ row.binding }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('aiAssistHotkeys.colActions')" width="200" align="center">
          <template #default="{ row }">
            <el-button
              v-if="recordingCommand !== row.id"
              size="small"
              text
              type="primary"
              data-test="hotkey-record"
              @click="startRecording(row.id)"
            >
              {{ t('aiAssistHotkeys.record') }}
            </el-button>
            <el-button
              v-else
              size="small"
              text
              data-test="hotkey-cancel-record"
              @click="stopRecording()"
            >
              {{ t('aiAssistHotkeys.cancelRecord') }}
            </el-button>
            <el-button
              size="small"
              text
              :disabled="row.binding === defaultBinding(row.id)"
              data-test="hotkey-reset"
              @click="resetHotkey(row.id)"
            >
              {{ t('aiAssistHotkeys.resetDefault') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div style="display: flex; align-items: center; gap: 12px;">
        <el-button
          type="primary"
          :loading="settingsSaving"
          :disabled="!canSaveSettings"
          data-test="settings-save"
          @click="saveSettings"
        >
          {{ t('aiAssistHotkeys.save') }}
        </el-button>
        <span v-if="!canSaveSettings" style="font-size: 12px; color: var(--el-text-color-secondary);">
          {{ t('aiAssistHotkeys.superAdminOnly') }}
        </span>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue';
import { useI18n } from 'vue-i18n';
const { t } = useI18n();
import { MagicStick, Tickets, Key } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { inferenceAgentApi } from '@/api/inference';
import type { AgentsSettingsView } from '@/api/inference';
import { assistantSettingsApi } from '@/api/assistant';
import { DEFAULT_HOTKEYS, captureHotkeyFromEvent } from '@/utils/hotkey';
import { useAuthStore } from '@/stores/auth';

const auth = useAuthStore();

// ── Agent 管理（与既有交互一致：表格工作副本，保存才提交）────────────
const BUILTIN_AGENT_IDS = ['claude', 'codex', 'kimi'];

interface AgentRow {
  id: string;
  displayName: string;
  kind: 'acp';
  command: string;
  args: string[];
  enabled: boolean;
  available: boolean;
  detail?: string;
  builtin: boolean;
}

const canManageAgents = computed(() => !!auth.isAdmin);
const agentRows = ref<AgentRow[]>([]);
const agentsDefault = ref<string | null>(null);
const agentsLoading = ref(false);
const agentsSaving = ref(false);
const agentsProbing = ref(false);
const agentDialogVisible = ref(false);
const agentFormMode = ref<'add' | 'edit'>('add');
const agentFormEditingBuiltin = ref(false);
const agentForm = ref({ id: '', displayName: '', command: '', argsText: '' });
const agentFormError = ref('');

function applyAgentsView(view: AgentsSettingsView) {
  agentsDefault.value = view.defaultProvider;
  agentRows.value = (view.agents || []).map((a) => ({
    id: a.id,
    displayName: a.displayName,
    kind: a.kind,
    command: a.command || '',
    args: [...(a.args || [])],
    enabled: a.enabled,
    available: a.available,
    detail: a.detail,
    builtin: a.builtin,
  }));
}

async function loadAgents() {
  if (!canManageAgents.value) return;
  agentsLoading.value = true;
  try {
    applyAgentsView(await inferenceAgentApi.getAgents());
  } catch {
    // 拦截器已提示；403 等场景留空表格
  } finally {
    agentsLoading.value = false;
  }
}

async function probeAgents() {
  agentsProbing.value = true;
  try {
    applyAgentsView(await inferenceAgentApi.probeAgents());
  } catch {
    // 拦截器已提示
  } finally {
    agentsProbing.value = false;
  }
}

async function saveAgents() {
  agentsSaving.value = true;
  try {
    // 默认提供方必须是已启用 Agent，否则置空交由后端回退
    const defaultProvider =
      agentsDefault.value && agentRows.value.some((r) => r.id === agentsDefault.value && r.enabled)
        ? agentsDefault.value
        : null;
    const view = await inferenceAgentApi.saveAgents({
      defaultProvider,
      agents: agentRows.value.map((r) => ({
        id: r.id,
        displayName: r.displayName,
        enabled: r.enabled,
        command: r.command || undefined,
        args: r.args.length ? [...r.args] : undefined,
      })),
    });
    applyAgentsView(view);
    ElMessage.success(t('systemInference.saveSuccess'));
  } catch {
    // 拦截器已提示
  } finally {
    agentsSaving.value = false;
  }
}

/** 停用某 Agent 时同步清除其默认标记（默认只能在已启用集合中选择） */
function onAgentEnabledChange(row: AgentRow, enabled: boolean | string | number) {
  if (!enabled && agentsDefault.value === row.id) {
    agentsDefault.value = null;
  }
}

function openAgentDialog(mode: 'add' | 'edit', row?: AgentRow) {
  agentFormMode.value = mode;
  agentFormError.value = '';
  if (mode === 'add') {
    agentFormEditingBuiltin.value = false;
    agentForm.value = { id: '', displayName: '', command: '', argsText: '' };
  } else if (row) {
    agentFormEditingBuiltin.value = row.builtin;
    agentForm.value = {
      id: row.id,
      displayName: row.displayName,
      command: row.command,
      argsText: (row.args || []).join('\n'),
    };
  }
  agentDialogVisible.value = true;
}

function removeAgent(row: AgentRow) {
  agentRows.value = agentRows.value.filter((r) => r.id !== row.id);
  if (agentsDefault.value === row.id) {
    agentsDefault.value = null;
  }
}

function confirmAgentDialog() {
  const id = agentForm.value.id.trim();
  const command = agentForm.value.command.trim();
  const args = agentForm.value.argsText
    .split('\n')
    .map((s) => s.trim())
    .filter(Boolean);
  if (agentFormMode.value === 'add') {
    if (!/^[a-z0-9]+(-[a-z0-9]+)*$/.test(id)) {
      agentFormError.value = t('systemInference.idInvalid');
      return;
    }
    if (BUILTIN_AGENT_IDS.includes(id) || agentRows.value.some((r) => r.id === id)) {
      agentFormError.value = t('systemInference.idDuplicate');
      return;
    }
    if (!command) {
      agentFormError.value = t('systemInference.commandRequired');
      return;
    }
    agentRows.value = [
      ...agentRows.value,
      {
        id,
        displayName: agentForm.value.displayName.trim() || id,
        kind: 'acp',
        command,
        args,
        enabled: true,
        available: false,
        builtin: false,
      },
    ];
  } else {
    const row = agentRows.value.find((r) => r.id === id);
    if (!row) return;
    if (!command) {
      agentFormError.value = t('systemInference.commandRequired');
      return;
    }
    if (!agentFormEditingBuiltin.value) {
      row.displayName = agentForm.value.displayName.trim() || id;
    }
    row.command = command;
    row.args = args;
  }
  agentDialogVisible.value = false;
}

// ── 生成偏好 + 快捷键（GET 限 REGISTRAR；PUT 限 SUPER_ADMIN）────────
const candidateCount = ref<number>(3);
const hotkeys = ref<Record<string, string>>({ ...DEFAULT_HOTKEYS });
const settingsSaving = ref(false);
const settingsLoadFailed = ref(false);

const canSaveSettings = computed(() => !!auth.isAdmin);

/** 命令清单：首条为调试侧栏开合（服务端默认合并保证存在） */
const hotkeyRows = computed(() =>
  Object.keys(hotkeys.value).sort().map((id) => ({
    id,
    descriptionKey: id === 'aiDebug.toggle' ? 'aiAssistHotkeys.descAiDebugToggle' : id,
    binding: hotkeys.value[id] ?? '',
  })),
);

function defaultBinding(id: string): string {
  return DEFAULT_HOTKEYS[id] ?? '';
}

// 录制态：全局 keydown 采集；Esc 取消
const recordingCommand = ref<string | null>(null);

function startRecording(commandId: string) {
  recordingCommand.value = commandId;
  window.addEventListener('keydown', onCaptureKeydown, true);
}

function stopRecording() {
  recordingCommand.value = null;
  window.removeEventListener('keydown', onCaptureKeydown, true);
}

function onCaptureKeydown(event: KeyboardEvent) {
  if (!recordingCommand.value) return;
  event.preventDefault();
  event.stopPropagation();
  if (event.key === 'Escape') {
    stopRecording();
    return;
  }
  const binding = captureHotkeyFromEvent(event);
  if (!binding) return; // 仅修饰键，继续等待主键
  hotkeys.value = { ...hotkeys.value, [recordingCommand.value]: binding };
  stopRecording();
}

function resetHotkey(commandId: string) {
  hotkeys.value = { ...hotkeys.value, [commandId]: defaultBinding(commandId) };
}

async function loadSettings() {
  try {
    const settings = await assistantSettingsApi.get();
    candidateCount.value = settings.candidateCount;
    hotkeys.value = { ...DEFAULT_HOTKEYS, ...settings.hotkeys };
    settingsLoadFailed.value = false;
  } catch {
    settingsLoadFailed.value = true;
  }
}

async function saveSettings() {
  settingsSaving.value = true;
  try {
    const settings = await assistantSettingsApi.save({
      candidateCount: candidateCount.value,
      hotkeys: { ...hotkeys.value },
    });
    candidateCount.value = settings.candidateCount;
    hotkeys.value = { ...DEFAULT_HOTKEYS, ...settings.hotkeys };
    ElMessage.success(t('aiAssistHotkeys.saveSuccess'));
  } catch {
    // 拦截器已提示
  } finally {
    settingsSaving.value = false;
  }
}

onMounted(() => {
  loadAgents();
  loadSettings();
});

onBeforeUnmount(stopRecording);
</script>

<style scoped>
.agent-command {
  font-size: 12px;
  background: var(--el-fill-color-light);
  padding: 2px 6px;
  border-radius: 4px;
  word-break: break-all;
}

.agent-args {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
  word-break: break-all;
}

.agent-detail {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
}

.prefs-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}

.hotkey-capture {
  display: inline-block;
  padding: 2px 10px;
  font-size: 12px;
  color: var(--el-color-primary);
  border: 1px dashed var(--el-color-primary);
  border-radius: 4px;
  background: var(--el-color-primary-light-9);
  animation: hotkey-pulse 1.2s ease-in-out infinite;
}

@keyframes hotkey-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.55; }
}
</style>
