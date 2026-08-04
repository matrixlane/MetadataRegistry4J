<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="560px"
    :close-on-click-modal="false"
    destroy-on-close
    @closed="reset"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="auto"
    >
      <slot name="fields" :form="form" />
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSubmit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import type { FormInstance, FormRules } from 'element-plus';

interface Props {
  title: string;
  initial?: Record<string, unknown>;
  rules?: FormRules;
  submitFn: (data: Record<string, unknown>) => Promise<unknown>;
}

const props = defineProps<Props>();
const emit = defineEmits<{ success: [] }>();

const visible = ref(false);
const saving = ref(false);
const formRef = ref<FormInstance>();
const form = reactive<Record<string, any>>({});

function open(initial?: Record<string, unknown>) {
  reset();
  Object.assign(form, props.initial || {});
  if (initial) Object.assign(form, initial);
  visible.value = true;
}

function reset() {
  Object.keys(form).forEach((k) => delete (form as Record<string, unknown>)[k]);
  formRef.value?.resetFields();
}

async function handleSubmit() {
  if (!formRef.value) return;
  await formRef.value.validate(async (valid) => {
    if (!valid) return;
    saving.value = true;
    try {
      await props.submitFn({ ...(form as Record<string, unknown>) });
      ElMessage.success('保存成功');
      visible.value = false;
      emit('success');
    } catch (err: any) {
      // submitFn implementations usually show their own ElMessage.error;
      // swallow the error here so it does not become an unhandled rejection.
      console.error('提交失败', err);
    } finally {
      saving.value = false;
    }
  });
}

defineExpose({ open });
</script>
