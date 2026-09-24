import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useBatchDelete } from './useBatchDelete';
import { withSilentErrors } from '@/utils/request';

vi.mock('element-plus', () => ({
  ElMessage: {
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
  },
  ElMessageBox: {
    confirm: vi.fn(),
  },
}));

vi.mock('@/utils/request', () => ({
  withSilentErrors: vi.fn((fn: () => unknown) => fn()),
}));

// 与 zh-CN.json 保持一致的消息表 + {placeholder} 插值
vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string, params?: Record<string, unknown>) => {
      const messages: Record<string, string> = {
        'common.batchDeleteConfirm': '确认删除选中的 {count} 条记录？',
        'common.batchDeleteSuccess': '已删除 {count} 条记录',
        'common.batchDeleteResult': '成功 {success} 条，失败 {fail} 条{reason}',
      };
      let message = messages[key] ?? key;
      for (const [k, v] of Object.entries(params ?? {})) {
        message = message.replaceAll(`{${k}}`, String(v));
      }
      return message;
    },
  }),
}));

const confirmMock = vi.mocked(ElMessageBox.confirm);
const withSilentErrorsMock = vi.mocked(withSilentErrors);

function setup(
  deleteResults: Array<() => Promise<unknown>> = [],
  extra: { preCheck?: (rows: Record<string, unknown>[]) => Promise<string | void> } = {}
) {
  const deleteFn = vi.fn((id: string) => deleteResults[deleteFn.mock.calls.length - 1]());
  const reload = vi.fn().mockResolvedValue(undefined);
  const clearSelection = vi.fn();
  const batch = useBatchDelete({
    deleteFn,
    reload,
    getTableRef: () => ({ clearSelection }),
    ...extra,
  });
  return { ...batch, deleteFn, reload, clearSelection };
}

beforeEach(() => {
  vi.clearAllMocks();
  confirmMock.mockResolvedValue(undefined as never);
});

describe('useBatchDelete', () => {
  it('全部成功：按 id 并发调用 deleteFn，成功汇总并清空选中、刷新', async () => {
    const { deleteFn, reload, clearSelection, handleSelectionChange, handleBatchDelete } =
      setup([() => Promise.resolve(), () => Promise.resolve(), () => Promise.resolve()]);
    handleSelectionChange([{ id: 'a' }, { id: 'b' }, { id: 'c' }]);

    await handleBatchDelete();

    expect(confirmMock).toHaveBeenCalledTimes(1);
    expect(deleteFn).toHaveBeenCalledTimes(3);
    expect(deleteFn.mock.calls.map((c) => c[0]).sort()).toEqual(['a', 'b', 'c']);
    expect(ElMessage.success).toHaveBeenCalledWith('已删除 3 条记录');
    expect(ElMessage.warning).not.toHaveBeenCalled();
    expect(clearSelection).toHaveBeenCalledTimes(1);
    expect(reload).toHaveBeenCalledTimes(1);
  });

  it('部分失败：warning 汇总附首条失败原因，仍清空选中并刷新', async () => {
    const { deleteFn, reload, clearSelection, handleSelectionChange, handleBatchDelete } = setup([
      () => Promise.resolve(),
      () => Promise.reject(new Error('被引用，无法删除')),
      () => Promise.resolve(),
    ]);
    handleSelectionChange([{ id: 'a' }, { id: 'b' }, { id: 'c' }]);

    await handleBatchDelete();

    expect(deleteFn).toHaveBeenCalledTimes(3);
    expect(ElMessage.success).not.toHaveBeenCalled();
    expect(ElMessage.warning).toHaveBeenCalledWith('成功 2 条，失败 1 条：被引用，无法删除');
    expect(clearSelection).toHaveBeenCalledTimes(1);
    expect(reload).toHaveBeenCalledTimes(1);
  });

  it('全部失败：warning 汇总，异常原因为字符串时直接展示', async () => {
    const { handleSelectionChange, handleBatchDelete, reload } = setup([
      () => Promise.reject(new Error('网络错误')),
      () => Promise.reject('冲突'),
    ]);
    handleSelectionChange([{ id: 'a' }, { id: 'b' }]);

    await handleBatchDelete();

    expect(ElMessage.warning).toHaveBeenCalledWith('成功 0 条，失败 2 条：网络错误');
    expect(reload).toHaveBeenCalledTimes(1);
  });

  it('用户取消确认：不调用 deleteFn，不刷新', async () => {
    confirmMock.mockRejectedValue('cancel' as never);
    const { deleteFn, reload, handleSelectionChange, handleBatchDelete } = setup();
    handleSelectionChange([{ id: 'a' }]);

    await handleBatchDelete();

    expect(deleteFn).not.toHaveBeenCalled();
    expect(reload).not.toHaveBeenCalled();
    expect(ElMessage.success).not.toHaveBeenCalled();
    expect(ElMessage.warning).not.toHaveBeenCalled();
  });

  it('未选中或执行中时不触发确认', async () => {
    const { deleteFn, handleBatchDelete } = setup();
    await handleBatchDelete();
    expect(confirmMock).not.toHaveBeenCalled();
    expect(deleteFn).not.toHaveBeenCalled();
  });

  it('批量执行包在 withSilentErrors 内（抑制逐条错误 toast）', async () => {
    const { handleSelectionChange, handleBatchDelete } = setup([() => Promise.resolve()]);
    handleSelectionChange([{ id: 'a' }]);
    await handleBatchDelete();
    expect(withSilentErrorsMock).toHaveBeenCalledTimes(1);
  });

  it('preCheck 返回提示串时拼进确认消息', async () => {
    const preCheck = vi.fn().mockResolvedValue('其中 2 个命名空间包含子命名空间或标识符，将被一并删除');
    const { deleteFn, handleSelectionChange, handleBatchDelete } = setup(
      [() => Promise.resolve(), () => Promise.resolve()],
      { preCheck }
    );
    const rows = [{ id: 'a' }, { id: 'b' }];
    handleSelectionChange(rows);

    await handleBatchDelete();

    expect(preCheck).toHaveBeenCalledWith(rows);
    expect(confirmMock).toHaveBeenCalledWith(
      '确认删除选中的 2 条记录？\n其中 2 个命名空间包含子命名空间或标识符，将被一并删除',
      '提示',
      { type: 'warning' }
    );
    expect(deleteFn).toHaveBeenCalledTimes(2);
  });

  it('preCheck 无提示时确认消息不带附加串', async () => {
    const preCheck = vi.fn().mockResolvedValue(undefined);
    const { handleSelectionChange, handleBatchDelete } = setup([() => Promise.resolve()], { preCheck });
    handleSelectionChange([{ id: 'a' }]);

    await handleBatchDelete();

    expect(confirmMock).toHaveBeenCalledWith('确认删除选中的 1 条记录？', '提示', { type: 'warning' });
  });

  it('preCheck reject：中止，不弹确认、不调用 deleteFn', async () => {
    const preCheck = vi.fn().mockRejectedValue(new Error('deletionCheck 失败'));
    const { deleteFn, reload, handleSelectionChange, handleBatchDelete } = setup(
      [() => Promise.resolve()],
      { preCheck }
    );
    handleSelectionChange([{ id: 'a' }]);

    await handleBatchDelete();

    expect(confirmMock).not.toHaveBeenCalled();
    expect(deleteFn).not.toHaveBeenCalled();
    expect(reload).not.toHaveBeenCalled();
  });

  it('reload reject：不向上抛，批量流程正常结束', async () => {
    const reload = vi.fn().mockRejectedValue(new Error('刷新失败'));
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});
    const batch = useBatchDelete({
      deleteFn: vi.fn().mockResolvedValue(undefined),
      reload,
    });
    batch.handleSelectionChange([{ id: 'a' }]);

    await expect(batch.handleBatchDelete()).resolves.toBeUndefined();
    expect(reload).toHaveBeenCalledTimes(1);
    consoleError.mockRestore();
  });
});
