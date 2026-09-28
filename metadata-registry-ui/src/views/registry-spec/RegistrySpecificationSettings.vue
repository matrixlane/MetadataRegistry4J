  <template>
  <div>
    <h2 style="margin-top: 0;">{{ $t('nav.registrySpecification') }}</h2>
    <DataTablePage
      ref="tableRef"
      :fetch-fn="registrySpecificationApi.list"
      :delete-fn="registrySpecificationApi.delete"
      @create="openDialog()"
      @edit="(r) => openDialog(r)"
      @delete="handleDelete"
    >
      <template #columns>
        <el-table-column prop="name" label="名称" sortable />
        <el-table-column prop="standard" label="标准" />
        <el-table-column prop="degreeOfConformance" label="合规程度" width="140" />
        <el-table-column prop="characterRepertoire" label="字符集" />
      </template>
    </DataTablePage>

    <el-dialog
      v-model="visible"
      :title="isEdit ? '编辑注册规范' : '新建注册规范'"
      width="720px"
      :close-on-click-modal="false"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="130px"
      >
        <el-form-item label="名称" prop="name" required>
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="注释">
          <el-input v-model="form.comment" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="网址">
          <el-input v-model="form.webAddress" />
        </el-form-item>
        <el-form-item label="字符集" prop="characterRepertoire" required>
          <el-input v-model="form.characterRepertoire" />
        </el-form-item>
        <el-form-item label="标准">
          <el-input v-model="form.standard" placeholder="例如：ISO/IEC 11179-3:2023" />
        </el-form-item>
        <el-form-item label="合规程度">
          <el-select v-model="form.degreeOfConformance" clearable style="width: 100%;">
            <el-option label="合规" value="CONFORMING" />
            <el-option label="严格合规" value="STRICTLY_CONFORMING" />
          </el-select>
        </el-form-item>
        <el-form-item label="合规等级">
          <el-input v-model="form.conformanceLevel" />
        </el-form-item>
        <el-form-item label="合规标签">
          <el-input v-model="form.conformanceLabel" />
        </el-form-item>
        <el-form-item label="包含特性">
          <el-input v-model="form.includedFeatures" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="排除特性">
          <el-input v-model="form.excludedFeatures" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="默认时区" prop="defaultTimeZone">
          <el-input v-model="form.defaultTimeZone" placeholder="[-+]HHMM，如 +0800、-0230" />
        </el-form-item>
        <el-form-item label="默认语言">
          <el-select v-model="form.defaultLanguageId" clearable placeholder="选择默认语言" style="width: 100%;">
            <template #label="{ label, value }">
              <span v-if="isPendingLanguage(value)">…</span>
              <span v-else>{{ label }}</span>
            </template>
            <el-option
              v-for="lang in languageOptions"
              :key="lang.id"
              :label="lang.languageIdentifier"
              :value="lang.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="替代语言">
          <el-select
            v-model="form.alternativeLanguageIds"
            multiple
            clearable
            placeholder="选择注册表中使用的其他语言"
            style="width: 100%;"
          >
            <el-option
              v-for="lang in languageOptions"
              :key="lang.id"
              :label="lang.languageIdentifier"
              :value="lang.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="上下文">
          <el-select
            v-model="form.contextId"
            clearable
            filterable
            placeholder="注册表自身的默认上下文"
            style="width: 100%;"
          >
            <el-option
              v-for="ctx in contextOptions"
              :key="ctx.id"
              :label="ctx.label"
              :value="ctx.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="表示类方案标识">
          <el-input v-model="form.representationClassSchemeId" placeholder="注册表表示类分类方案标识" />
        </el-form-item>
        <el-form-item label="参考文档标识种类">
          <el-select
            v-model="form.referenceDocumentIdentifierKinds"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="输入后回车添加，如 URI、内部文档编号"
            style="width: 100%;"
          >
            <el-option
              v-for="kind in form.referenceDocumentIdentifierKinds"
              :key="kind"
              :label="kind"
              :value="kind"
            />
          </el-select>
        </el-form-item>
        <el-divider content-position="left">指称与定义</el-divider>
        <DesignationDefinitionSection
          :designations="form.designations"
          :definitions="form.definitions"
          :show-pairings="false"
          :default-language-id="defaultLanguageId || undefined"
          @update:designations="form.designations = $event"
          @update:definitions="form.definitions = $event"
        />
        <el-divider content-position="left">注册标识</el-divider>
        <ItemRegistrationSection
          ref="regSection"
          v-model="form.registration"
          :is-editing="isEdit"
          :registration-authority-name="editingRaName"
        />
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { registrySpecificationApi, languageApi, contextApi } from '@/api/resources';
import DataTablePage from '@/components/DataTablePage.vue';
import ItemRegistrationSection from '@/components/ItemRegistrationSection.vue';
import DesignationDefinitionSection from '@/components/DesignationDefinitionSection.vue';
import { useRegistryDefaultLanguage } from '@/composables/useRegistryDefaultLanguage';
import { useDefaultContext } from '@/composables/useDefaultContext';
import {
  createEmptyRegistrySpecForm,
  resetRegistrySpecForm,
  applyRowToForm,
  buildRegistrySpecPayload,
  timeZoneValidator,
  type RegistrySpecFormModel,
} from './registrySpecForm';
import type { FormInstance, FormRules } from 'element-plus';
import { ElMessage } from 'element-plus';

const tableRef = ref<InstanceType<typeof DataTablePage>>();
const formRef = ref<FormInstance>();
const visible = ref(false);
const saving = ref(false);
const isEdit = ref(false);
const regSection = ref<InstanceType<typeof ItemRegistrationSection>>();
const editingRaName = ref<string | null>(null);
const languageOptions = ref<{ id: string; languageIdentifier: string }[]>([]);
const contextOptions = ref<{ id: string; label: string }[]>([]);
const languagesLoaded = ref(false);
const { defaultLanguageId, load: loadDefaultLanguage } = useRegistryDefaultLanguage();
const { defaultContextId, load: loadDefaultContext } = useDefaultContext();

const form = reactive(createEmptyRegistrySpecForm()) as RegistrySpecFormModel;

/** 语言列表加载完成前，未命中的选中值显示占位而非原始 id */
function isPendingLanguage(value: unknown): boolean {
  if (!value || typeof value !== 'string') return false;
  if (languagesLoaded.value) return false;
  return !languageOptions.value.some((l) => l.id === value);
}

const rules: FormRules = {
  name: [{ required: true, message: '名称不能为空', trigger: 'blur' }],
  characterRepertoire: [{ required: true, message: '字符集不能为空', trigger: 'blur' }],
  defaultTimeZone: [{ validator: timeZoneValidator, trigger: 'blur' }],
};

onMounted(async () => {
  loadDefaultLanguage();
  loadDefaultContext();
  try {
    const res = await languageApi.list({ size: 100 });
    languageOptions.value = (res.content || []).map((lang: any) => ({
      id: lang.id,
      languageIdentifier: lang.languageIdentifier || lang.identifier || '—',
    }));
  } catch {
    // ignore
  } finally {
    languagesLoaded.value = true;
  }
  try {
    contextOptions.value = (await contextApi.options()) || [];
  } catch {
    // ignore
  }
});

function resetForm() {
  resetRegistrySpecForm(form);
  formRef.value?.resetFields();
}

async function openDialog(row?: Record<string, any>) {
  resetForm();
  isEdit.value = !!row?.id;
  editingRaName.value = row?.meta?.registrationAuthorityName || null;
  if (row) {
    applyRowToForm(form, row);
    form.registration = {
      namespaceId: row.meta?.namespaceId || undefined,
      identifier: row.meta?.identifier || undefined,
      version: row.meta?.version || undefined,
    };
  } else {
    // 新建时以系统默认上下文（Registry Context）预填，用户可变更
    await loadDefaultContext();
    if (!form.contextId && defaultContextId.value) {
      form.contextId = defaultContextId.value;
    }
  }
  visible.value = true;
}

async function submit() {
  if (!formRef.value) return;
  await formRef.value.validate(async (valid) => {
    if (!valid) return;
    const varError = regSection.value?.validateTemplateVariables() ?? null;
    if (varError) {
      ElMessage.error(varError);
      return;
    }
    saving.value = true;
    try {
      const payload = buildRegistrySpecPayload(form, regSection.value?.buildPayload());
      if (isEdit.value && form.id) {
        await registrySpecificationApi.update(form.id, payload);
      } else {
        await registrySpecificationApi.create(payload);
      }
      ElMessage.success('保存成功');
      visible.value = false;
      tableRef.value?.fetchData();
    } finally {
      saving.value = false;
    }
  });
}

async function handleDelete(row: Record<string, unknown>) {
  await registrySpecificationApi.delete(row.id as string);
  tableRef.value?.fetchData();
}
</script>
