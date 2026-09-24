# 实施计划：add-item-list-batch-delete

日期：2026-09-21
输入：`openspec/changes/add-item-list-batch-delete/`（proposal/design/spec/tasks 已校验通过）、`docs/superpowers/specs/2026-09-21-item-list-batch-delete-design.md`

## 顺序

1. **request.ts 静默标记**：模块级 `let silenceCount = 0`，导出 `withSilentErrors<T>(fn: () => Promise<T>): Promise<T>`（try/finally 计数增减）；响应拦截器两个错误分支在 `silenceCount > 0` 时跳过 `ElMessage.error`（401 登出、498/499 特殊流程不动）。
2. **useBatchDelete composable**（`src/composables/useBatchDelete.ts`）：
   - props：`deleteFn: (id: string) => Promise<unknown>`、`reload: () => void`（或返回 Promise）、可选 `getTableRef?: () => { clearSelection?: () => void } | undefined`。
   - 返回：`selectedRows`、`selectedCount`（computed）、`batchDeleting`、`handleSelectionChange`、`handleBatchDelete`、`clearSelection`。
   - `handleBatchDelete`：`ElMessageBox.confirm(\`确认删除选中的 ${n} 条记录？\`, '提示', { type: 'warning' })` → cancel 直接 return → `withSilentErrors(async () => { allSettled... })` → 全成功 `ElMessage.success(\`已删除 ${ok} 条记录\`)`；有失败 `ElMessage.warning(\`成功 ${ok} 条，失败 ${fail} 条${firstReason ? '：' + firstReason : ''}\`)` → finally：`clearSelection()` + `await reload()`。
   - 失败原因从 rejected reason 提取：reason instanceof Error ? reason.message : String(reason)。
3. **DataTablePage.vue**：
   - props 加 `deleteFn?: (id: string) => Promise<unknown>`；computed `batchEnabled = props.canDelete && !!props.deleteFn`。
   - `batchEnabled` 时：`<el-table-column type="selection" width="46" />` 置 `<slot name="columns" />` 之前；`@selection-change="handleSelectionChange"` 绑 el-table。
   - 表格卡片内、el-table 上方加批量条：`已选 {selectedCount} 条` + `<el-button type="danger" :icon="Delete" :disabled="selectedCount===0 || batchDeleting" :loading="batchDeleting" @click="handleBatchDelete">批量删除</el-button>`。
   - composable 调用：`useBatchDelete({ deleteFn: (id) => props.deleteFn!(id), reload: fetchData, getTableRef: () => tableRef.value })`；模板 el-table 加 `ref="tableRef"`。
   - 注意 fetchData 后 selection 自动清空（rows 重渲染），clearSelection 防御性调用即可。
4. **页面接入**：grep 找出 `views/**` 中所有使用 `DataTablePage` 且监听 `@delete` 的 List.vue；为每个补 `:delete-fn="xxxApi.delete"`（与该页面 `handleDelete` 中调用的同一个 api.delete 保持一致）。`AdministeredItemList.vue` 改 `:can-delete="false"`。NamespaceList 传 `namespaceApi.delete`（不包 deletionCheck）。
5. **ItemMappingList.vue**：自绘 el-table 操作列前加 selection 列 + `@selection-change`；表格上方工具区加批量条；复用 useBatchDelete，`reload` 为其现有取数函数，删除后调用。
6. **测试**：
   - `src/composables/useBatchDelete.spec.ts`。
   - `src/components/DataTablePage.spec.ts`：mock fetchFn 返回 PageResponse；用 mount + ElementPlus 插件（参照既有 spec 的挂载方式，如 ValueDomainList.spec.ts）；未传 deleteFn 断言无 selection 列/批量按钮；勾选行（直接调组件暴露或触发 selection-change）→ 点批量删除 → ElMessageBox.confirm mock resolve → 断言 deleteFn 调用次数与 id → 断言成功消息与 fetchData 再调用；部分失败场景断言 warning。
   - 全量 `npm test` 回归。
7. **验证**：`npm run build`、`npm test` 全绿；`node .gitnexus/run.cjs detect-changes --scope all --repo .` 无 partial/truncated。

## 红线

- 不改后端。
- 不改删除权限/状态/引用语义。
- 未传 `deleteFn` 的页面渲染结果必须与现状一致（既有 spec 不破坏）。
- UI 文案用简体中文，风格与现有 DataTablePage 一致。
