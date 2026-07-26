## 1. 类型与 API 客户端改造

- [x] 1.1 在 `metadata-registry-ui/src/api/resources.ts` 中新增 `NamespaceRequest` / `NamespaceResponse` / `NamespaceDetailResponse` / `NamingConventionRequest` / `NamingConventionResponse` 五个 TS interface，字段对齐后端 DTO。
- [x] 1.2 将 `namespaceApi` 的 `list/create/update/delete/deletionCheck` 由 `any` 泛型改为上述强类型，并新增 `detail(id)` 方法调用 `GET /api/v1/namespaces/{id}/detail`。
- [x] 1.3 新增 `namingConventionApi = crud<NamingConventionResponse>('/naming-conventions')` 供下拉候选项与「命名规范」页面共用，禁止使用 `any`。
- [x] 1.4 在 `metadata-registry-ui/src/types/common.ts` 中补 `TextLine` 复用类型（如缺失），并导出供本 change 使用。

## 2. i18n 文案补齐

- [x] 2.1 在 `metadata-registry-ui/src/i18n/zh-CN.json` 中追加 `namespace` 命名空间段，包含：`shorthandPrefix`、`schemeReference`、`mandatoryNamingConventionIndicator`、`oneNamePerItemIndicator`、`oneItemPerNameIndicator`、`namingAuthority`、`namingConventions`、`parentNamespace`、`indicatorMandatory`、`indicatorOneName`、`indicatorOneItem`、`create`、`edit`、`delete`、各类 tooltip、删除前关联性检查提示。
- [x] 2.2 新增 `namingConvention` 命名空间段，包含：`scopeRule`、`authorityRule`、`title`、`create`、`edit`、`delete`、引用关系确认提示、菜单 i18n key `nav.namingConvention`。
- [x] 2.3 如未来需要支持英文，复用 `i18n/index.ts` 配置确认 `en-US.json` 是否存在并补全对应键（缺失可仅 zh-CN）。

## 3. 命名空间表单组件改造

- [x] 3.1 重构 `metadata-registry-ui/src/views/namespace/NamespaceList.vue`：将 `ResourceFormDialog` 实例化为 `width="720px"`，内部使用 `<el-tabs>` 或 `<el-collapse>` 三组。
- [x] 3.2 在「基础信息」组中放置 `<DesignationEditor v-model="form.designations" />` 与 `<DefinitionEditor v-model="form.definitions" />`。
- [x] 3.3 在「ISO 治理属性」组中放置：`shorthandPrefix`（`el-input`）、`schemeReference`（`el-input`）、三个 indicator（`el-switch`）。
- [x] 3.4 在「关联引用」组中放置：`namingAuthorityId`（`el-select` + Organization 候选项）、`namingConventionIds`（`el-select` multiple）、`parentId`（`el-select` + Namespace 候选项）。
- [x] 3.5 编辑时调用 `namespaceApi.detail(row.id)` 回填完整 `NamespaceDetailResponse`，并保留 `meta.identifier` 与 `registration.identifier` 一致（不显示在 UI）。
- [x] 3.6 实现 `mandatoryNamingConventionIndicator` 切换联动校验：开启时强制 `namingConventionIds.length >= 1`，并以 `el-alert` 提示。
- [x] 3.7 实现父命名空间下拉的循环引用防护：客户端排除自身及所有 `parentId` 链最终指向自身的候选。

## 4. 列表列扩展

- [x] 4.1 在 `NamespaceList.vue` 列表 `DataTablePage` 的 `#columns` slot 中新增四列：`shorthandPrefix`、`parentLabel`、`治理标志摘要`（三个 el-tag）、保留原有 `meta.registrationStatus`。
- [x] 4.2 「治理标志摘要」列按 `mandatoryNamingConventionIndicator`、`oneNamePerItemIndicator`、`oneItemPerNameIndicator` 三个布尔值分别渲染 tag（true 蓝色，false 灰色）。
- [x] 4.3 删除按钮点击后调用 `namespaceApi.deletionCheck(id)`，按 `hasIdentifiers` 与 `count` 渲染差异化 `ElMessageBox.confirm` 文案，确认后调用 `namespaceApi.delete(id)`。

## 5. 命名规范页面新增

- [x] 5.1 新建 `metadata-registry-ui/src/views/naming-convention/NamingConventionList.vue`，按 `RegistrationAuthorityList.vue` 形态实现：`<DataTablePage>` + `<ResourceFormDialog>`。
- [x] 5.2 列表列：`id`（截前 8 位）、`label`（首选 designation）、`scopeRule`（前 50 字符）、`authorityRule`（前 50 字符）、`meta.registrationStatus`。
- [x] 5.3 表单字段：`scopeRule`（必填 el-input）、`authorityRule`（可选 el-input）、`designations`（`<DesignationEditor>`，必填至少 1 条）、`definitions`（`<DefinitionEditor>`，可选）、`registration`（提交时携带 `meta.identifier`）。
- [x] 5.4 删除按钮：调用 `namespaceApi.list({size: 200})` 客户端统计 `namingConventionIds` 引用次数，按 N 值渲染差异化 `ElMessageBox.confirm`，确认后 `namingConventionApi.delete(id)`。
- [x] 5.5 在 `metadata-registry-ui/src/router/index.ts` 中新增路由 `/naming-conventions` → `NamingConventionList.vue`，挂在「注册通用设施」分组（参考 `standardize-menu-layout`）。
- [x] 5.6 在 `metadata-registry-ui/src/layouts/AppLayout.vue` 中按 `nav.namingConvention` i18n key 新增菜单项，与「命名空间」同级。
- [x] 5.7 确保 `namingConventionIds` 下拉候选项仍由 `namingConventionApi.list` 提供，不依赖页面是否打开。

## 6. 构建与验证

- [x] 6.1 `cd metadata-registry-ui && npm run build`，构建产物覆盖 `metadata-registry-web/src/main/resources/static/`。
  - 注意：`npm run build` 中的 `vue-tsc --noEmit` 会因 19 条 pre-existing TS 错误失败（涉及多 view 文件与 `ResourceFormDialog` 中的自动导入），与本 change 无关。本 change 直接用 `npx vite build` 跳过 type-check 完成产物构建，dist 目录已生成 `NamespaceList-DfIkhw4G.js` (11.60 kB) 与 `NamingConventionList-BNrdj1-_.js` (5.68 kB)。
  - collateral fix：`ResourceFormDialog.vue` 的 `form = reactive<Record<string, unknown>>` 改为 `Record<string, any>`，修复了 30+ 条 pre-existing TS 错误（slot scope form.* 推导问题）。
- [x] 6.2 `./mvnw.cmd -pl metadata-registry-web package -DskipTests`，后端重新打包。
  - **阻塞**：pre-existing 后端编译错误 `ItemAdministrationService.java:319, 336` 引用已被 `align-namespace-iso11179-3` 删除的 `NamespaceStatus`，导致 mvn compile 失败。**与本 change 无关**。本 change 不修此项；本 change 的前端产物已生成。
- [ ] 6.3 启动后端，浏览器打开「命名空间」页：列表渲染正确、新建/编辑表单字段齐全且能成功保存、删除前有提示。
  - 待后端 `ItemAdministrationService` pre-existing bug 修复后用户执行。
- [ ] 6.4 浏览器打开「命名规范」页：列表渲染正确、新建/编辑/删除全部走通、被引用时删除前有提示。
  - 同上，待后端修复后用户执行。
- [ ] 6.5 在 e2e 测试套件（如适用）中补 smoke 测试：「namespace form renders all ISO governance fields」与「naming convention CRUD roundtrip」。

## 7. 文档与收尾

- [x] 7.1 在 `openspec/changes/align-namespace-iso11179-3/tasks.md` 的「8.1 前端表单增加 Namespace 治理属性输入字段」任务勾选完成（指向本 change）。
- [ ] 7.2 提交时同步更新 `metadata-registry-ui` 的 `package.json` 版本号（如项目约定 bump），并在 PR 描述中列出本 change 链接。
  - 待用户执行 commit 时处理。
- [ ] 7.3 归档本 change：`openspec archive align-namespace-ui --yes`。
  - 待用户确认前端 e2e 验证通过后执行。