<template>
  <div class="registry-spec-settings-page">
    <h2 style="margin-top: 0;">注册规范</h2>

    <el-card class="settings-card mb-6" shadow="never" v-loading="loadingSpec">
      <template #header>
        <div class="card-header">
          <span class="card-title">基础规范设置</span>
          <el-tag v-if="!isNew" type="success" size="small" effect="plain">已配置</el-tag>
          <el-tag v-else type="warning" size="small" effect="plain">待配置</el-tag>
        </div>
      </template>

      <!-- Warning if registry spec is missing -->
      <el-alert
        v-if="isNew"
        type="warning"
        show-icon
        :closable="false"
        style="margin-bottom: 20px;"
      >
        <template #title>
          <strong>尚未配置注册规范</strong>
        </template>
        <template #default>
          只有创建了注册规范后，本注册表才正式允许进行数据治理和项目注册。请填写下方表单完成配置。
        </template>
      </el-alert>

      <!-- Alert if not authorized to edit -->
      <el-alert
        v-if="!canEdit"
        type="info"
        show-icon
        :closable="false"
        style="margin-bottom: 20px;"
      >
        <template #title>
          <strong>只读模式</strong>
        </template>
        <template #default>
          您当前的账号角色无权修改注册规范。仅系统管理员和注册管理员允许修改规范配置。
        </template>
      </el-alert>

      <!-- Alert if metadata exists -->
      <el-alert
        v-if="!isNew && hasMetadata"
        type="info"
        show-icon
        :closable="false"
        style="margin-bottom: 20px;"
      >
        <template #title>
          <strong>规则已锁定</strong>
        </template>
        <template #default>
          检测到系统中已存在元数据，标识符前缀、生成策略、版本策略、强制分类及默认语言已被锁定，不可修改。
        </template>
      </el-alert>

      <el-form
        ref="specFormRef"
        :model="specForm"
        :rules="rulesSpec"
        label-position="top"
        :disabled="!canEdit"
      >
        <el-form-item label="注册规范名称" prop="registryName">
          <template #label>
            <div class="form-label-with-tip">
              <span>注册规范名称</span>
              <el-tooltip content="为此注册规范命名的友好展示名称" placement="top">
                <el-icon class="help-icon"><QuestionFilled /></el-icon>
              </el-tooltip>
            </div>
          </template>
          <el-input
            v-model="specForm.registryName"
            placeholder="例如：主元数据注册表规范"
            size="large"
            clearable
          />
        </el-form-item>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="标识符生成策略" prop="autoGenerateIdentifier">
              <template #label>
                <div class="form-label-with-tip">
                  <span>标识符生成策略</span>
                  <el-tooltip content="决定新注册项的唯一标识符是系统自动生成还是手动输入" placement="top">
                    <el-icon class="help-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </div>
              </template>
              <el-radio-group v-model="specForm.autoGenerateIdentifier" :disabled="!isNew && hasMetadata">
                <el-radio-button :value="true">自动生成</el-radio-button>
                <el-radio-button :value="false">手动输入</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="标识符前缀" prop="identifierPrefix">
              <template #label>
                <div class="form-label-with-tip">
                  <span>标识符前缀</span>
                  <el-tooltip content="自动生成标识符时的前缀，例如 SYS 会生成 SYS-000001" placement="top">
                    <el-icon class="help-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </div>
              </template>
              <el-input
                v-model="specForm.identifierPrefix"
                placeholder="例如：SYS"
                size="large"
                clearable
                :disabled="(!isNew && hasMetadata) || !specForm.autoGenerateIdentifier"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="版本化策略" prop="versioningPolicy">
              <template #label>
                <div class="form-label-with-tip">
                  <span>版本化策略</span>
                  <el-tooltip content="决定新版本项的版本号生成规范（语义化 X.Y.Z 或单递增整数）" placement="top">
                    <el-icon class="help-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </div>
              </template>
              <el-select v-model="specForm.versioningPolicy" size="large" placeholder="请选择版本规则" style="width: 100%;" :disabled="!isNew && hasMetadata">
                <el-option label="语义化版本 (X.Y.Z)" value="SEMVER" />
                <el-option label="单整数版本 (1, 2, 3)" value="INTEGRAL" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="默认语言" prop="defaultLanguage">
              <template #label>
                <div class="form-label-with-tip">
                  <span>默认语言</span>
                  <el-tooltip content="注册表项默认的语言，例如简体中文 (zh-CN) 或英文 (en-US)" placement="top">
                    <el-icon class="help-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </div>
              </template>
              <el-select v-model="specForm.defaultLanguage" size="large" placeholder="请选择默认语言" style="width: 100%;" :disabled="!isNew && hasMetadata">
                <el-option label="简体中文 (zh-CN)" value="zh-CN" />
                <el-option label="英文 (en-US)" value="en-US" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="强制分类规范" prop="mandatoryClassification">
              <template #label>
                <div class="form-label-with-tip">
                  <span>强制分类规范</span>
                  <el-tooltip content="启用后，所有提交注册的元数据项必须关联至少一个分类方案节点" placement="top">
                    <el-icon class="help-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </div>
              </template>
              <el-switch v-model="specForm.mandatoryClassification" size="large" :disabled="!isNew && hasMetadata" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手动标识校验正则" prop="identifierPattern">
              <template #label>
                <div class="form-label-with-tip">
                  <span>手动标识校验正则</span>
                  <el-tooltip content="仅在手动输入模式下生效，校验输入的标识符是否符合特定的字符集" placement="top">
                    <el-icon class="help-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </div>
              </template>
              <el-input
                v-model="specForm.identifierPattern"
                placeholder="例如：^[A-Z0-9_-]+$"
                size="large"
                clearable
                :disabled="(!isNew && hasMetadata) || specForm.autoGenerateIdentifier"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="描述" prop="description">
          <template #label>
            <div class="form-label-with-tip">
              <span>描述</span>
            </div>
          </template>
          <el-input
            v-model="specForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入关于此注册规范的简要描述"
          />
        </el-form-item>

        <div v-if="canEdit" style="display: flex; justify-content: flex-end; margin-top: 20px;">
          <el-button
            type="primary"
            size="large"
            :loading="savingSpec"
            style="width: 140px;"
            @click="saveSpecification"
          >
            保存配置
          </el-button>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { ElMessage } from 'element-plus';
import { QuestionFilled } from '@element-plus/icons-vue';
import { registrySpecificationApi, type RegistrySpecification } from '@/api/system';
import { useAuthStore } from '@/stores/auth';

const auth = useAuthStore();

// Admins and Registrars can edit the registry specification
const canEdit = computed(() => auth.isRegistrar);

const specForm = ref<RegistrySpecification>({
  registryName: '',
  identifierPrefix: '',
  autoGenerateIdentifier: true,
  identifierPattern: '^[A-Z0-9_-]+$',
  versioningPolicy: 'SEMVER',
  mandatoryClassification: false,
  defaultLanguage: 'zh-CN',
  description: '',
});

const isNew = ref(true);
const loadingSpec = ref(false);
const savingSpec = ref(false);
const hasMetadata = ref(false);

const rulesSpec = {
  registryName: [
    { required: true, message: '请输入注册规范名称', trigger: 'blur' },
    { min: 2, max: 100, message: '长度在 2 到 100 个字符', trigger: 'blur' }
  ],
  defaultLanguage: [
    { required: true, message: '请选择默认语言', trigger: 'change' }
  ],
  versioningPolicy: [
    { required: true, message: '请选择版本规则', trigger: 'change' }
  ]
};

const specFormRef = ref();

async function loadSpecification() {
  loadingSpec.value = true;
  try {
    const res = await registrySpecificationApi.get();
    if (res) {
      specForm.value = res;
      isNew.value = false;
    } else {
      isNew.value = true;
    }
    
    // Check if metadata exists
    hasMetadata.value = await registrySpecificationApi.hasMetadata();
  } catch (err) {
    console.error('Failed to load registry specification', err);
  } finally {
    loadingSpec.value = false;
  }
}

async function saveSpecification() {
  if (!specFormRef.value) return;
  
  let valid = false;
  await specFormRef.value.validate((v: boolean) => {
    valid = v;
  });
  if (!valid) return;
  
  savingSpec.value = true;
  try {
    if (isNew.value) {
      const saved = await registrySpecificationApi.create(specForm.value);
      ElMessage.success('注册规范创建成功！');
      specForm.value = saved;
      isNew.value = false;
    } else {
      const saved = await registrySpecificationApi.update(specForm.value.id!, specForm.value);
      ElMessage.success('注册规范更新成功！');
      specForm.value = saved;
    }
    hasMetadata.value = await registrySpecificationApi.hasMetadata();
  } catch (err) {
    // Error handled by Axios response interceptor
  } finally {
    savingSpec.value = false;
  }
}

onMounted(() => {
  loadSpecification();
});
</script>

<style scoped>
.registry-spec-settings-page {
  max-width: 800px;
}

.settings-card {
  margin-bottom: 24px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.form-label-with-tip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.help-icon {
  font-size: 14px;
  color: var(--el-text-color-secondary);
  cursor: help;
  vertical-align: middle;
}

.mb-6 {
  margin-bottom: 24px !important;
}
</style>
