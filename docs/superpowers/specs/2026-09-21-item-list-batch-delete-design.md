# 设计：Item 列表批量操作（多选/全选 + 批量删除）

日期：2026-09-21
变更：add-item-list-batch-delete
状态：已确认（auto 模式自动推进，确认门自动通过）

## 背景与探索结论

- 前端约 27 个列表页统一使用共享组件 `metadata-registry-ui/src/components/DataTablePage.vue`（内部固定渲染 `el-table` + 操作列 + 删除确认框）。例外：`ItemMappingList.vue` 自绘表格；`InteropMappingList` 的"删除"实为撤销，排除；`ClassificationTreeBrowser` 是树，排除。
- 全前端无任何 `el-table-column type="selection"` 多选先例，需从零建设。
- 后端每个类型已有单条 `DELETE /api/v1/{type}/{id}`（SecurityConfig：`DELETE /api/v1/**` → SUPER_ADMIN/REGISTRAR），无任何批量删除端点；删除无注册状态约束；引用冲突由 JPA 外键 → `GlobalExceptionHandler` 转 409 CONFLICT。
- 前端请求封装 `utils/request.ts` 已统一解包 `{code,message,data}`，业务代码拿到 `data`，错误自动 ElMessage 并 reject。
- 既有 bug（不在本变更修复，仅规避）：`AdministeredItemList.vue` 的删除按钮调用不存在的后端端点（`AdministeredItemController` 无 `DELETE /{id}`）。

## 标准依据

Milvus 标准库（anythingllm_metadata_registry）无 BM25/sparse 字段，临时关键词查询不可用，回退结论依据既有会话已核实的 ISO/IEC 11179-6:2023：注册生命周期以状态（recorded/qualified 等）治理，硬删除不在标准注册流程内——但本应用硬删除早已存在（Registrar/SuperAdmin 操作）。本变更**不改变删除语义**，仅将既有单条删除在 UI 层批量化，与 11179-6 无冲突。

## 目标

- 每个 Item 列表页支持行多选（含表头全选当前页），选中后可一键批量删除。
- 批量删除有确认框（显示选中数量），执行后给出成功/失败汇总，单条失败不阻塞其余。

## 非目标

- 不新增后端批量删除端点（逐条 DELETE 语义逐条独立，冲突项自然跳过）。
- 不做跨页全选（全选 = 当前页全选）。
- 不改动删除权限、状态约束与引用检查语义。
- 不修复 AdministeredItemList 删除端点缺失的既有 bug（仅隐藏其删除入口）。

## 方案对比

| 方案 | 说明 | 结论 |
| --- | --- | --- |
| A（选定） | 纯前端：DataTablePage 加 selection 列 + 批量删除工具栏，复用既有单条 DELETE API（Promise.allSettled 并发） | 27 页一次接入、零后端改动、权限/错误码语义完全复用 |
| B | 后端各 Controller 新增 `POST /{type}/batch-delete` | 需改 ~15 个 Controller+Service，引入事务/部分失败语义分歧，收益低 |

## 设计细节（方案 A）

### 共享逻辑提取：`useBatchDelete` composable

`metadata-registry-ui/src/composables/useBatchDelete.ts`：

- 输入：`deleteFn: (id: string) => Promise<unknown>`、刷新回调 `reload: () => void`。
- 状态：`selectedRows`（ref 数组）、`batchDeleting`（ref）。
- `handleSelectionChange(rows)`：直接绑定 el-table `@selection-change`。
- `handleBatchDelete()`：
  1. `ElMessageBox.confirm('确认删除选中的 N 条记录？', '提示', { type: 'warning' })`；
  2. `batchDeleting = true`，`Promise.allSettled(selected.map(r => deleteFn(r.id)))` 并发执行；
  3. 汇总：全成功 → `ElMessage.success('已删除 N 条')`；部分/全部失败 → `ElMessage.warning('成功 X 条，失败 Y 条：{首条错误消息}')`；
  4. 清空选中（`tableRef.clearSelection()`，由页面/composable 持有的 table ref 调用）并 `reload()`。

说明：并发数不设限（ Registrar 批量操作规模通常为几十条以内；如后续需要可加 p-limit）。

### DataTablePage.vue 改动

- `el-table` 最前插入 `<el-table-column type="selection" width="46" />`（`row-key="id"` 已具备）。
- 新增 props：`deleteFn?: (id: string) => Promise<unknown>`；`canDelete` 为 false 或未传 `deleteFn` 时不渲染 selection 列与批量按钮（即旧页面行为完全不变，向后兼容）。
- 表格卡片顶部新增批量操作条：显示"已选 N 条" + 「批量删除」危险按钮（选中 0 条时禁用）。
- 内部使用 `useBatchDelete`，`reload = fetchData`；向 selection 列透传 `@selection-change`。

### 页面接入（机械改动，约 26 页）

为使用 DataTablePage 且已有删除能力的列表页补 `:delete-fn="xxxApi.delete"`，例如：

```vue
<DataTablePage
  ...
  :delete-fn="valueDomainApi.delete"
  @delete="handleDelete"
/>
```

特殊情况：
- `NamespaceList.vue`：单条删除有 `deletionCheck` 预检对话框；批量路径直接传 `namespaceApi.delete`，引用冲突由后端 409 汇总体现，不逐条预检（批量场景预检交互成本过高，语义一致）。
- `AdministeredItemList.vue`：传 `:can-delete="false"`，隐藏其删除按钮与批量入口（后端端点缺失的既有 bug 一并规避），并在 proposal 中记录遗留。

### ItemMappingList.vue（自绘表格）

加 `type="selection"` 列（置于操作列前）+ 表格上方批量删除工具栏，复用同一 `useBatchDelete`，`deleteFn = itemMappingApi.delete`。

### 权限与错误语义

- 权限：不变。DELETE 端点仍要求 SUPER_ADMIN/REGISTRAR；VIEWER 点击删除得到既有 403 处理（计入失败汇总）。
- 失败可见性：汇总消息附首条失败原因；逐条请求由 `request.ts` 拦截器对 `code!==0` 自动弹 ElMessage.error——批量场景会产生多条错误 toast，需在 useBatchDelete 执行期间抑制逐条错误提示（方案：执行前设置一个模块级静默标记，`request.ts` 响应拦截器检查该标记时只 reject 不弹 toast；或 axios 支持 per-request 配置静默——优选后者：request 封装若支持自定义 config 则传 `{ silent: true }`，不支持则加模块级标记）。

## 测试策略

- 新增 `src/components/DataTablePage.spec.ts`（vitest + @vue/test-utils）：
  - 未传 `deleteFn` 时不渲染 selection 列/批量按钮；
  - 选择行后点击批量删除 → 确认框 → deleteFn 被以选中 id 调用 → 成功汇总消息 → fetchData 被重新调用；
  - 部分失败 → warning 汇总消息，成功项仍被删除并刷新。
- 新增 `useBatchDelete` 单测（成功/部分失败/全部失败/取消确认）。
- 现有各列表页 spec（ValueDomainList.spec.ts 等）作为回归：selection 列新增不应破坏既有断言。
- 验证命令：`cd metadata-registry-ui && npm run build && npm test`。
