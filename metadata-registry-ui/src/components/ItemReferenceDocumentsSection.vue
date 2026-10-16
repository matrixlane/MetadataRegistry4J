<template>
  <div class="item-reference-documents-section" data-test="reference-documents-section">
    <div class="refdoc-toolbar">
      <el-select
        v-model="pendingIds"
        multiple
        filterable
        remote
        clearable
        collapse-tags
        collapse-tags-tooltip
        :remote-method="search"
        :placeholder="t('itemReferenceDocuments.searchPlaceholder')"
        style="flex: 1;"
        data-test="reference-document-picker"
      >
        <el-option
          v-for="d in candidates"
          :key="d.id"
          :label="d.title || d.identifier"
          :value="d.id"
        >
          <span class="refdoc-option">
            <span class="refdoc-option-id">{{ d.identifier || '—' }}</span>
            <span v-if="d.title" class="refdoc-option-name">{{ d.title }}</span>
          </span>
        </el-option>
      </el-select>
      <el-button
        type="primary"
        class="refdoc-action-btn"
        :disabled="pendingIds.length === 0"
        data-test="reference-document-add"
        @click="emitPending"
      >
        {{ t('itemReferenceDocuments.add') }}
      </el-button>
    </div>

    <el-empty
      v-if="selected.length === 0"
      :description="t('itemReferenceDocuments.empty')"
      :image-size="72"
      data-test="reference-document-empty"
    />
    <ul v-else class="refdoc-list" data-test="reference-document-list">
      <li class="refdoc-item refdoc-item-header">
        <span class="refdoc-col-id">{{ t('itemReferenceDocuments.identifierColumn') }}</span>
        <span class="refdoc-col-title">{{ t('itemReferenceDocuments.titleColumn') }}</span>
        <span class="refdoc-item-actions" />
      </li>
      <li
        v-for="d in selected"
        :key="d.id"
        class="refdoc-item refdoc-item-clickable"
        data-test="reference-document-row"
        @click="openDocument(d)"
      >
        <span class="refdoc-col-id">{{ d.identifier || '—' }}</span>
        <span class="refdoc-col-title">{{ d.title || '—' }}</span>
        <span v-if="!isReadonly" class="refdoc-item-actions" @click.stop>
          <el-button
            type="danger"
            plain
            class="refdoc-action-btn"
            data-test="reference-document-remove"
            @click="remove(d.id)"
          >
            {{ t('itemReferenceDocuments.remove') }}
          </el-button>
        </span>
      </li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { ElMessage } from 'element-plus';
import { referenceDocumentApi } from '../api/resources';
import { useAuthStore } from '../stores/auth';
import type { ReferenceDocument } from '../types/common';

const props = defineProps<{
  /** 条目已关联的参考文档 id（ItemMetaResponse.referenceDocumentIds） */
  modelValue?: string[];
  /** 只读模式（新建态无受管链时不渲染本区块，由宿主控制） */
  readonly?: boolean;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', ids: string[]): void;
  (e: 'add', ids: string[]): void;
  (e: 'remove', ids: string[]): void;
}>();

const { t } = useI18n();
const isReadonly = computed(() => props.readonly === true);

const selected = ref<ReferenceDocument[]>([]);
const candidates = ref<ReferenceDocument[]>([]);
const pendingIds = ref<string[]>([]);

/**
 * hydrate 异步代次：每次调用递增，await 期间若有更新的 hydrate 请求，
 * 过期结果直接丢弃——否则慢请求返回时会用旧 modelValue 覆盖较新的移除状态，
 * 表现为已删除的参考文档在界面上"复活"（后端已删成功，用户反复点移除）。
 */
let hydrateSeq = 0;

/** 父组件只持有 id；这里按需补齐可展示的标识符与标题。 */
async function hydrate(ids: string[]) {
  const seq = ++hydrateSeq;
  if (ids.length === 0) {
    selected.value = [];
    return;
  }
  const wanted = new Set(ids);
  const known = selected.value.filter((d) => wanted.has(d.id));
  const missing = ids.filter((id) => !known.some((d) => d.id === id));
  if (missing.length === 0) {
    selected.value = ids.map((id) => known.find((d) => d.id === id)!);
    return;
  }
  const listed = await search('');
  if (seq !== hydrateSeq) return;
  // 列表结果（首页 50 条）未覆盖的 id 逐个查询详情，避免详情缺失时只能回退显示原始 id。
  const resolved = await Promise.all(
    missing.map(async (id) => {
      const fromList = listed.find((d) => d.id === id);
      if (fromList) return fromList;
      try {
        const res = await referenceDocumentApi.get(id);
        return (res ?? null) as ReferenceDocument | null;
      } catch {
        return null;
      }
    }),
  );
  if (seq !== hydrateSeq) return;
  selected.value = ids.map(
    (id) => known.find((d) => d.id === id) ?? resolved[missing.indexOf(id)] ?? { id, identifier: id },
  );
}

async function search(keyword: string): Promise<ReferenceDocument[]> {
  const res = await referenceDocumentApi.list({ page: 0, size: 50, q: keyword });
  // 响应拦截器已拆出 ApiResponse.data，这里拿到的就是分页对象本身。
  const items = ((res as any)?.content ?? []) as ReferenceDocument[];
  candidates.value = items;
  return items;
}

function emitPending() {
  const ids = [...pendingIds.value];
  pendingIds.value = [];
  if (ids.length === 0) return;
  const merged = [...selected.value.map((d) => d.id), ...ids.filter((id) => !selected.value.some((d) => d.id === id))];
  emit('update:modelValue', merged);
  emit('add', ids);
}

function remove(id: string) {
  emit('update:modelValue', selected.value.filter((d) => d.id !== id).map((d) => d.id));
  emit('remove', [id]);
}

/**
 * 点击参考文档行时按 URI 打开文档。
 * 本地文件路径（Windows 盘符/UNC/file://）经后端代理读取后在新标签页打开；
 * http(s) 先用 HEAD 探测资源是否存在，存在则新标签页打开；
 * 资源不存在、URI 缺失/非法或网络不可达时提示重新指定有效的参考文档 URI。
 */
async function openDocument(doc: ReferenceDocument) {
  const raw = (doc.uri ?? '').trim();
  if (!raw) {
    ElMessage.error(t('itemReferenceDocuments.notFound'));
    return;
  }
  if (isLocalFileUri(raw)) {
    await openLocalDocument(doc.id);
    return;
  }
  let url: URL | null = null;
  try {
    // 相对路径按当前站点 origin 解析，便于支持站内文档链接。
    url = new URL(raw, window.location.origin);
  } catch {
    url = null;
  }
  if (!url || (url.protocol !== 'http:' && url.protocol !== 'https:')) {
    ElMessage.error(t('itemReferenceDocuments.notFound'));
    return;
  }
  try {
    const res = await fetch(url.href, { method: 'HEAD' });
    if (res.ok) {
      window.open(url.href, '_blank', 'noopener,noreferrer');
      return;
    }
  } catch {
    // 网络失败或跨域受限：无法确认资源存在，落到统一提示。
  }
  ElMessage.error(t('itemReferenceDocuments.notFound'));
}

/** Windows 盘符路径（E:\...）、UNC（\\server\share）或 file:// URI。 */
function isLocalFileUri(raw: string): boolean {
  return /^[a-zA-Z]:[\\/]/.test(raw) || raw.startsWith('\\\\') || /^file:\/\//i.test(raw);
}

/**
 * 本地文件经后端代理（GET /reference-documents/{id}/content）读取为 Blob 后在新标签页打开。
 * 用 fetch 而非 axios：响应是文件流而非 ApiResponse 信封，且需携带鉴权头；
 * 404/异常时落到统一提示。
 */
async function openLocalDocument(id: string) {
  const apiBase = ((import.meta.env.VITE_API_BASE as string | undefined) ?? '/api/v1').replace(/\/$/, '');
  const url = `${apiBase}/reference-documents/${id}/content`;
  const token = useAuthStore().token;
  try {
    const res = await fetch(url, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
    if (!res.ok) {
      throw new Error(`HTTP ${res.status}`);
    }
    const blob = await res.blob();
    const objectUrl = URL.createObjectURL(blob);
    window.open(objectUrl, '_blank', 'noopener,noreferrer');
    // 新标签页加载需要时间，延迟释放；提前释放会导致页面空白。
    setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);
  } catch {
    ElMessage.error(t('itemReferenceDocuments.notFound'));
  }
}

watch(
  () => props.modelValue,
  (ids) => {
    void hydrate(ids ?? []);
  },
  { immediate: true, deep: true },
);

onMounted(() => {
  void search('');
});
</script>

<style scoped>
.refdoc-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.refdoc-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.refdoc-item {
  display: grid;
  grid-template-columns: 140px 1fr 64px;
  column-gap: 12px;
  align-items: center;
  padding: 6px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.refdoc-item-header {
  font-weight: 600;
  color: var(--el-text-color-regular);
}
.refdoc-item-clickable {
  cursor: pointer;
}
.refdoc-item-clickable:hover .refdoc-col-title {
  color: var(--el-color-primary);
  text-decoration: underline;
}
.refdoc-col-id {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.refdoc-col-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 与每行「移除」按钮同宽，右对齐成一条竖线。 */
.refdoc-action-btn {
  width: 64px;
}
.refdoc-item-actions {
  display: flex;
  justify-content: flex-end;
}
.refdoc-option {
  display: flex;
  gap: 8px;
}
.refdoc-option-name {
  color: var(--el-text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>