  <template>
  <div>
    <h2 style="margin-top: 0;">{{ $t('nav.registrySpecification') }}</h2>
    <DataTablePage
      ref="tableRef"
      :fetch-fn="registrySpecificationApi.list"
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
      width="680px"
      :close-on-click-modal="false"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="120px"
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
          <el-input v-model="form.standard" />
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
        <el-form-item label="默认时区">
          <el-input v-model="form.defaultTimeZone" placeholder="+0800" />
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
        <el-form-item label="中文名称" prop="zhName">
          <el-input v-model="form.zhName" />
        </el-form-item>
        <el-form-item label="英文名称" prop="enName">
          <el-input v-model="form.enName" />
        </el-form-item>
        <el-form-item label="中文定义">
          <el-input v-model="form.zhDefinition" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="英文定义">
          <el-input v-model="form.enDefinition" type="textarea" :rows="2" />
        </el-form-item>
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
import { registrySpecificationApi, languageApi } from '@/api/resources';
import DataTablePage from '@/components/DataTablePage.vue';
import ItemRegistrationSection from '@/components/ItemRegistrationSection.vue';
import type { FormInstance, FormRules } from 'element-plus';
import { ElMessage } from 'element-plus';
import type { TextLineForm } from '@/types/common';

const tableRef = ref<InstanceType<typeof DataTablePage>>();
const formRef = ref<FormInstance>();
const visible = ref(false);
const saving = ref(false);
const isEdit = ref(false);
const regSection = ref<InstanceType<typeof ItemRegistrationSection>>();
const editingRaName = ref<string | null>(null);
const languages = ref<Record<string, string>>({});
const languageOptions = ref<{ id: string; languageIdentifier: string }[]>([]);
const languagesLoaded = ref(false);

/** 语言列表加载完成前，未命中的选中值显示占位而非原始 id */
function isPendingLanguage(value: unknown): boolean {
  if (!value || typeof value !== 'string') return false;
  if (languagesLoaded.value) return false;
  return !languageOptions.value.some((l) => l.id === value);
}

const form = reactive<Record<string, any>>({
  name: '',
  comment: '',
  webAddress: '',
  defaultLanguageId: '',
  characterRepertoire: '',
  standard: '',
  degreeOfConformance: undefined,
  conformanceLevel: '',
  conformanceLabel: '',
  includedFeatures: '',
  excludedFeatures: '',
  defaultTimeZone: '',
  zhName: '',
  enName: '',
  zhDefinition: '',
  enDefinition: '',
  registration: {},
});

const rules: FormRules = {
  name: [{ required: true, message: '名称不能为空', trigger: 'blur' }],
  characterRepertoire: [{ required: true, message: '字符集不能为空', trigger: 'blur' }],
};

onMounted(async () => {
  try {
    const res = await languageApi.list({ size: 100 });
    languageOptions.value = (res.content || []).map((lang: any) => ({
      id: lang.id,
      languageIdentifier: lang.languageIdentifier || lang.identifier || lang.id,
    }));
    (res.content || []).forEach((lang: any) => {
      const key = lang.languageIdentifier || lang.identifier || '';
      if (key) languages.value[key] = lang.id;
    });
  } catch {
    // ignore
  } finally {
    languagesLoaded.value = true;
  }
});

function resetForm() {
  Object.keys(form).forEach((k) => {
    if (k === 'degreeOfConformance') {
      form[k] = undefined;
    } else if (k === 'registration') {
      form[k] = {};
    } else {
      form[k] = '';
    }
  });
  form.defaultLanguageId = '';
  formRef.value?.resetFields();
}

function openDialog(row?: Record<string, any>) {
  resetForm();
  isEdit.value = !!row?.id;
  editingRaName.value = row?.meta?.registrationAuthorityName || null;
  if (row) {
    Object.assign(form, row);
    form.registration = {
      namespaceId: row.meta?.namespaceId || undefined,
      identifier: row.meta?.identifier || undefined,
      version: row.meta?.version || undefined,
    };
    form.defaultLanguageId = row.defaultLanguageId || '';
    const zh = row.designations?.find((d: any) => d.languageCode === 'zh-CN' || d.languageIdentifier === 'zh-CN')?.value || '';
    const en = row.designations?.find((d: any) => d.languageCode === 'en-US' || d.languageIdentifier === 'en-US')?.value || '';
    form.zhName = zh;
    form.enName = en;
    const zhDef = row.definitions?.find((d: any) => d.languageCode === 'zh-CN' || d.languageIdentifier === 'zh-CN')?.text || '';
    const enDef = row.definitions?.find((d: any) => d.languageCode === 'en-US' || d.languageIdentifier === 'en-US')?.text || '';
    form.zhDefinition = zhDef;
    form.enDefinition = enDef;
  }
  visible.value = true;
}

function buildTextLines(zhValue: string, enValue: string): TextLineForm[] {
  const lines: TextLineForm[] = [];
  if (zhValue && languages.value['zh-CN']) {
    lines.push({ languageId: languages.value['zh-CN'], value: zhValue });
  }
  if (enValue && languages.value['en-US']) {
    lines.push({ languageId: languages.value['en-US'], value: enValue });
  }
  return lines;
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
      const payload: Record<string, any> = {
        name: form.name,
        comment: form.comment || undefined,
        webAddress: form.webAddress || undefined,
        characterRepertoire: form.characterRepertoire,
        standard: form.standard || undefined,
        degreeOfConformance: form.degreeOfConformance || undefined,
        conformanceLevel: form.conformanceLevel || undefined,
        conformanceLabel: form.conformanceLabel || undefined,
        includedFeatures: form.includedFeatures || undefined,
        excludedFeatures: form.excludedFeatures || undefined,
        defaultTimeZone: form.defaultTimeZone || undefined,
        defaultLanguageId: form.defaultLanguageId || undefined,
        designations: buildTextLines(form.zhName, form.enName),
        definitions: buildTextLines(form.zhDefinition, form.enDefinition),
        registration: regSection.value?.buildPayload(),
      };
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
