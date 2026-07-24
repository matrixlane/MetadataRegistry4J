<template>
  <el-select
    :model-value="modelValue"
    :size="size"
    :clearable="clearable"
    :placeholder="placeholder"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-option label="首选" value="PREFERRED" />
    <el-option label="认可" value="ADMITTED" />
    <el-option label="已弃用" value="DEPRECATED" />
    <el-option label="已取代" value="SUPERSEDED" />
    <el-option label="已过时" value="OBSOLETE" />
  </el-select>
</template>

<script setup lang="ts">
type Acceptability = 'PREFERRED' | 'ADMITTED' | 'DEPRECATED' | 'SUPERSEDED' | 'OBSOLETE';

interface Props {
  modelValue?: Acceptability;
  size?: 'small' | 'default' | 'large';
  clearable?: boolean;
  placeholder?: string;
}

withDefaults(defineProps<Props>(), {
  modelValue: undefined,
  size: 'small',
  clearable: false,
  placeholder: '可接受性',
});

const emit = defineEmits<{
  'update:modelValue': [value: Acceptability | undefined];
}>();
</script>
