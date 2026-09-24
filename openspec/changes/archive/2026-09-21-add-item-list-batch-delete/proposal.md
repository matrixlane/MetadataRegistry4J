# Proposal: add-item-list-batch-delete

## Why

各注册对象类型的列表页（数据元、值域、概念域、对象类、特性、数据元概念、项目映射等约 27 个页面）目前仅支持逐条删除：每行一个删除按钮，逐条确认。管理注册项时经常需要清理多条记录（如导入失败的残留、批量试验数据），逐条删除操作成本高。需要在每个 Item 列表中提供多选/全选并批量删除的能力。

## What Changes

- 前端共享组件 `DataTablePage.vue` 新增行多选能力（`el-table-column type="selection"`，全选 = 当前页全选）与批量操作条（已选计数 + 「批量删除」按钮）。
- 新增 `useBatchDelete` composable 承载选中维护、确认框（显示选中数量）、并发执行（`Promise.allSettled`）与成功/失败汇总消息；单条失败不阻塞其余。
- 新增 prop `deleteFn`：`canDelete` 为 false 或未传 `deleteFn` 时完全不渲染多选列与批量入口（向后兼容，不影响未接入页面）。
- 各使用 DataTablePage 的列表页传入 `:delete-fn="xxxApi.delete"` 接入批量删除（纯前端复用既有单条 DELETE 端点，零后端改动）。
- 自绘表格的 `ItemMappingList.vue` 同样接入（selection 列 + 批量删除工具栏，复用同一 composable）。
- 批量执行期间抑制 `request.ts` 的逐条错误 toast（模块级静默标记），失败原因统一进入汇总消息。
- `AdministeredItemList.vue` 置 `:can-delete="false"`：其删除按钮调用的后端端点不存在（既有 bug），隐藏删除与批量入口并记录遗留。

## Capabilities

### New Capabilities

- `item-list-batch-operations`: Item 列表的多选/全选与批量删除交互契约（选择模型、确认与汇总反馈、静默错误、页面接入条件）。

### Modified Capabilities

（无既有能力的需求语义变更；删除权限、引用冲突 409 语义、状态约束均保持不变。）

## Impact

- `metadata-registry-ui`：新增 `composables/useBatchDelete.ts`；改 `components/DataTablePage.vue`、`utils/request.ts`（静默标记）、`api/resources.ts`（如涉及）；约 26 个 `views/**/**List.vue` 补 `:delete-fn`；`views/mapping/ItemMappingList.vue` 加 selection 列与批量条；`views/administered-item/AdministeredItemList.vue` 隐藏删除。
- 后端：无改动。删除仍走既有 `DELETE /api/v1/{type}/{id}`（SUPER_ADMIN/REGISTRAR）。
- 测试：新增 `DataTablePage.spec.ts` 与 `useBatchDelete` 单测；既有列表页 spec 回归。
- 标准依据：不改变删除语义，与 ISO/IEC 11179-6:2023 注册生命周期无冲突（详见 design.md）。
- 遗留：`AdministeredItemController` 缺 `DELETE /{id}` 端点（既有 bug，不在本变更修复）。
