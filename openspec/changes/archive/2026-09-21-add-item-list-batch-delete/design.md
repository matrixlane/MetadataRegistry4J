# Design: add-item-list-batch-delete

## Context

- 约 27 个列表页统一使用 `metadata-registry-ui/src/components/DataTablePage.vue`（固定渲染 `el-table` + 操作列 + 统一删除确认框）；`ItemMappingList.vue` 为自绘表格的唯一例外（排除 `InteropMappingList` 撤销语义与 `ClassificationTreeBrowser` 树形）。
- 全前端无 `type="selection"` 多选先例。
- 后端每类型已有单条 `DELETE /api/v1/{type}/{id}`；`SecurityConfig` 规定 `DELETE /api/v1/**` 需 SUPER_ADMIN/REGISTRAR；引用冲突由 JPA 外键 → `GlobalExceptionHandler` 转 409。
- `utils/request.ts` 响应拦截器对 `code!==0` 与 HTTP 错误自动 `ElMessage.error` 并 reject；`http.delete` 透传 axios config。

## Goals / Non-Goals

Goals：
- 每个 Item 列表支持行多选（表头全选 = 当前页全选）与一键批量删除。
- 确认框显示选中数量；执行后给出成功/失败汇总；单条失败不阻塞其余。
- 未接入页面（未传 `deleteFn`）行为与现状完全一致。

Non-Goals：
- 不新增后端批量删除端点（方案对比见下）。
- 不做跨页全选。
- 不改动权限/状态/引用检查语义。
- 不修复 `AdministeredItemController` 缺删除端点的既有 bug（仅隐藏 AdministeredItemList 删除入口）。

## Decisions

### D1：纯前端方案（选定），不建后端批量端点

| 方案 | 结论 |
| --- | --- |
| A. 前端共享组件 + 复用单条 DELETE（选定） | 27 页一次接入；零后端改动；权限与错误码语义完全复用；逐条独立请求使冲突项天然跳过 |
| B. 后端 `POST /{type}/batch-delete` | 需改 ~15 个 Controller+Service；引入事务与部分失败语义分歧；收益不匹配成本 |

### D2：共享逻辑抽为 `useBatchDelete` composable

`src/composables/useBatchDelete.ts`：维护 `selectedRows`/`batchDeleting`；`handleSelectionChange` 绑定 el-table；`handleBatchDelete` 做确认（显示 N 条）→ `Promise.allSettled` 并发 → 汇总消息 → 清空选中并刷新。DataTablePage 与 ItemMappingList 复用，避免双份实现。

### D3：执行期间抑制逐条错误 toast

`request.ts` 增加模块级静默标记（`silenceErrors` 计数 + `withSilentErrors(fn)`）；useBatchDelete 在批量执行期间开启静默，失败原因统一进入汇总消息（附首条失败原因）。选模块级标记而非 per-request config，因为 `crud()` 工厂生成的 `api.delete(id)` 不暴露 config 参数，避免侵入全部 API 定义。

### D4：批量入口渲染条件

`canDelete !== false` 且传入 `deleteFn` 时渲染 selection 列与批量操作条；否则与现状一致。NamespaceList 的 `deletionCheck` 预检仅在单条路径保留，批量路径依赖后端 409 汇总（语义一致，交互成本更低）。

### D5：AdministeredItemList 隐藏删除

`:can-delete="false"`：其后端删除端点不存在（既有 bug），隐藏单条与批量删除入口，遗留修复记录于 proposal。

## Risks / Trade-offs

- 大批量删除为 N 条并发请求：注册项批量操作规模通常几十条以内，可接受；如需限流后续可加 p-limit。
- 失败明细仅展示首条原因：汇总消息附首条错误信息，完整明细可后续在确认框/对话框中展开（非本期目标）。

## Verification

- `cd metadata-registry-ui && npm run build && npm test`。
- 新增 `DataTablePage.spec.ts`（未传 deleteFn 不渲染入口；选择→确认→删除→成功汇总→刷新；部分失败→warning 汇总）。
- 新增 `useBatchDelete` 单测（成功/部分失败/全部失败/取消）。
- 既有列表页 spec 回归（selection 列不破坏既有断言）。
