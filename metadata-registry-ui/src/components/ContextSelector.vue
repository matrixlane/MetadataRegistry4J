<template>
  <el-select
    :model-value="modelValue"
    :size="size"
    :clearable="clearable"
    :placeholder="placeholder"
    filterable
    remote
    reserve-keyword
    :remote-method="searchContexts"
    :loading="searching"
    @update:model-value="emit('update:modelValue', $event || undefined)"
  >
    <el-option
      v-for="opt in options"
      :key="opt.id"
      :label="opt.label"
      :value="opt.id"
    />
  </el-select>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { contextApi } from '@/api/resources';

interface Props {
  modelValue?: string;
  size?: 'small' | 'default' | 'large';
  clearable?: boolean;
  placeholder?: string;
}

withDefaults(defineProps<Props>(), {
  modelValue: undefined,
  size: 'small',
  clearable: true,
  placeholder: '选择上下文（可选）',
});

const emit = defineEmits<{
  'update:modelValue': [value: string | undefined];
}>();

const options = ref<{ id: string; label: string }[]>([]);
const searching = ref(false);

onMounted(async () => {
  await searchContexts('');
});

async function searchContexts(query: string) {
  searching.value = true;
  try {
    const res = await contextApi.options({ q: query });
    options.value = (res as any) || [];
  } catch {
    // ignore
  } finally {
    searching.value = false;
  }
}
</script>
