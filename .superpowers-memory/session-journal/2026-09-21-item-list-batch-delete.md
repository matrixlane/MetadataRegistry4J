# 2026-09-21 add-item-list-batch-delete（Item 列表批量删除）

## 交付内容

- 全部 Item 列表页获得「多选/全选（当前页）+ 批量删除」能力。方案 D1：纯前端，复用既有单条 DELETE 端点（Promise.allSettled 并发），零后端改动。
- 共享基础：`utils/request.ts` 模块级 `silenceCount` + `withSilentErrors`（批量执行期间抑制逐条错误 toast，401 登出/跳转保留）；新 composable `useBatchDelete.ts`（选中维护、确认框显示 N 条、并发删除、成功/失败汇总、清空选中并刷新；可选 `preCheck` 钩子）。
- `DataTablePage.vue`：新增 `deleteFn`/`batchPreCheck` prop，`canDelete!==false` 且传 `deleteFn` 时渲染 selection 列 + 批量条；未传则渲染与现状完全一致。25 个 DataTablePage 页面接入 `:delete-fn`；自绘表格 `ItemMappingList.vue` 单独接入。
- 评审修复（3 Major）：批量文案全量 i18n 化（common.batchDeleteConfirm/Success/Result/NamespaceHint + selectedCount/batchDelete，zh/en 同步）；NamespaceList 批量路径补 deletionCheck 预检（useBatchDelete 新增 preCheck 钩子，汇总含子项命名空间数拼入确认框，单条路径不动）；401 toast 纳入静默。reload reject 兜底、AdministeredItemList 死代码清理。
- `AdministeredItemList.vue` 置 `:can-delete="false"`：既有 bug——其删除按钮调用的 `AdministeredItemController` 无 `DELETE /{id}` 端点——本变更隐藏入口，**遗留修复**。

## 验证

- 前端 `npm run build`（vue-tsc + vite）通过；`npm test` 22 文件 / 215 用例全绿（新增 useBatchDelete 10 用例、DataTablePage 4 用例，既有零破坏）。
- `node .gitnexus/run.cjs detect-changes --scope all` 无 partial/truncated（risk critical 来自工作区大量既有未提交改动，非本次）。
- OpenSpec 已归档 `2026-09-21-add-item-list-batch-delete`，主 spec 新增能力 `item-list-batch-operations`（5 需求）；tasks 14/14 勾选。

## 遗留与注意

- `AdministeredItemController` 缺删除端点（既有 bug 待修复或产品决定隐藏即终态）。
- AppUserList、FederationNodesView 也接入了批量删除（账号 SUPER_ADMIN 限定、联邦节点删除同权限，均为显式范围决策）。
- `batchDeleteResult` 的失败原因前缀 `：` 在 en-US 下仍为中文冒号（文案 nit）。
- 未提交 git；提交需按 OpenSpec 变更分组并先 detect-changes（工作区有大量其他未提交改动）。
