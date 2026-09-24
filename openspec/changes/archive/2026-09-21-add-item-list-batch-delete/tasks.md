# Tasks: add-item-list-batch-delete

## 1. 共享基础

- [x] 1.1 `metadata-registry-ui/src/utils/request.ts` 增加模块级错误静默标记（`silenceErrors` 计数 + `withSilentErrors` 导出），响应拦截器在标记开启时跳过 `ElMessage.error` 仍正常 reject
- [x] 1.2 新增 `metadata-registry-ui/src/composables/useBatchDelete.ts`：维护 `selectedRows`/`batchDeleting`；`handleSelectionChange`；`handleBatchDelete`（确认框显示选中数 → `Promise.allSettled` 并发执行 `deleteFn` → 成功/失败汇总 `ElMessage` → 清空选中并调用 `reload`）；批量执行包在 `withSilentErrors` 内
- [x] 1.3 `useBatchDelete` 单测（vitest）：全部成功 / 部分失败 / 全部失败 / 用户取消确认

## 2. DataTablePage 组件

- [x] 2.1 `DataTablePage.vue` 新增 prop `deleteFn?: (id: string) => Promise<unknown>`；`canDelete !== false` 且传入 `deleteFn` 时渲染 `type="selection"` 列（置于最前）与批量操作条（已选 N 条 + 「批量删除」按钮，0 选中禁用）
- [x] 2.2 组件内使用 `useBatchDelete`，`reload` 复用 `fetchData`；`@selection-change` 绑定 composable；删除后清空表格选中
- [x] 2.3 新增 `src/components/DataTablePage.spec.ts`：未传 `deleteFn` 不渲染多选列与批量入口；勾选→批量删除→确认→`deleteFn` 按选中 id 调用→成功汇总→重新 fetch；部分失败→warning 汇总且仍刷新

## 3. 页面接入（DataTablePage 列表页）

- [x] 3.1 为使用 DataTablePage 且具备删除能力的列表页补 `:delete-fn="<对应Api>.delete"`（resources.ts 中 crud 工厂已生成的 delete）：数据元、数据元概念、值域、概念域、对象类、特性、数据类型、数据类型方案、值含义、计量单位、计量类、命名空间、上下文、注册机构、注册人员、组织、个人、联系人、角色、语言、参考文献、文档类型等，以 `views/**` 下实际使用 DataTablePage 且有 `@delete` 处理器的页面为准
- [x] 3.2 `views/namespace/NamespaceList.vue`：批量路径直接传 `namespaceApi.delete`（单条 deletionCheck 预检保留不变）
- [x] 3.3 `views/administered-item/AdministeredItemList.vue`：置 `:can-delete="false"`（后端删除端点缺失的既有 bug，隐藏删除与批量入口）

## 4. ItemMappingList（自绘表格）

- [x] 4.1 `views/mapping/ItemMappingList.vue`：操作列前加 `type="selection"` 列，`@selection-change` 绑定 `useBatchDelete`，表格上方加批量操作条（已选计数 + 批量删除按钮），`deleteFn = itemMappingApi.delete`

## 5. 验证与收尾

- [x] 5.1 `cd metadata-registry-ui && npm run build` 通过
- [x] 5.2 `npm test` 全绿（新增用例 + 既有列表页 spec 回归无破坏）
- [x] 5.3 运行 `node .gitnexus/run.cjs detect-changes --scope all --repo .`（或 MCP detect_changes），确认无 partial/truncated
- [x] 5.4 检查受影响文档：`README.md` 页面清单无需变更（无新页面/路由）则记录"无需更新"
