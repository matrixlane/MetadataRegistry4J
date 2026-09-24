import { ref, computed } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useI18n } from 'vue-i18n';
import { withSilentErrors } from '@/utils/request';

export interface BatchDeleteOptions {
  deleteFn: (id: string) => Promise<unknown>;
  reload: () => void | Promise<unknown>;
  getTableRef?: () => { clearSelection?: () => void } | undefined;
  /** 确认删除前的预检（如命名空间 deletionCheck）：resolve 出非空字符串则拼进确认消息，reject 则中止 */
  preCheck?: (rows: Record<string, unknown>[]) => Promise<string | void>;
}

export function useBatchDelete(options: BatchDeleteOptions) {
  const { t } = useI18n();
  const selectedRows = ref<Record<string, unknown>[]>([]);
  const batchDeleting = ref(false);

  const selectedCount = computed(() => selectedRows.value.length);

  function handleSelectionChange(rows: Record<string, unknown>[]) {
    selectedRows.value = rows;
  }

  function clearSelection() {
    selectedRows.value = [];
    options.getTableRef?.()?.clearSelection?.();
  }

  async function handleBatchDelete() {
    const rows = selectedRows.value;
    if (rows.length === 0 || batchDeleting.value) return;
    let hint: string | void;
    try {
      hint = await options.preCheck?.(rows);
    } catch {
      return;
    }
    let message = t('common.batchDeleteConfirm', { count: rows.length });
    if (typeof hint === 'string' && hint) {
      message += `\n${hint}`;
    }
    try {
      await ElMessageBox.confirm(message, '提示', { type: 'warning' });
    } catch {
      return;
    }
    batchDeleting.value = true;
    try {
      await withSilentErrors(async () => {
        const results = await Promise.allSettled(
          rows.map((row) => options.deleteFn(row.id as string))
        );
        const ok = results.filter((r) => r.status === 'fulfilled').length;
        const fail = results.length - ok;
        if (fail === 0) {
          ElMessage.success(t('common.batchDeleteSuccess', { count: ok }));
        } else {
          const firstRejected = results.find((r) => r.status === 'rejected') as
            | PromiseRejectedResult
            | undefined;
          const reason: unknown = firstRejected?.reason;
          const firstReason = reason instanceof Error ? reason.message : reason != null ? String(reason) : '';
          ElMessage.warning(
            t('common.batchDeleteResult', {
              success: ok,
              fail,
              reason: firstReason ? `：${firstReason}` : '',
            })
          );
        }
      });
    } finally {
      batchDeleting.value = false;
      clearSelection();
      try {
        await options.reload();
      } catch {
        // 刷新失败不阻断批量结果提示（错误已由 request 拦截器展示）
      }
    }
  }

  return {
    selectedRows,
    selectedCount,
    batchDeleting,
    handleSelectionChange,
    handleBatchDelete,
    clearSelection,
  };
}
