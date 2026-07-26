## Why

后端在 `align-namespace-iso11179-3`（commit 2077fd6，2026-07-24）已将与 ISO/IEC 11179-3:2023 对齐的全部治理属性下发到 `NamespaceRequest/Response/DetailResponse`，并加入 `NamespaceConstraintValidator` 进行强校验。但当时的 OpenSpec 提案在「不包含」段显式把"前端 UI 字段新增"排除在外，follow-up 任务 8.1「前端表单增加 Namespace 治理属性输入字段」至今仍为 unchecked。

当前 `metadata-registry-ui/src/views/namespace/NamespaceList.vue` 只暴露"标识符 / 中文名称 / 英文名称"三个字段，且提交体 `{ identifier, titleZh, titleEn }` 与后端 `NamespaceRequest`（`designations: List<TextLine>` + `registration: ItemRegistrationRequest` + 8 个治理字段）完全不兼容——直接提交会被 `@NotEmpty` 校验拦截。命名机构、命名规范、父命名空间、三个 ISO 治理 indicator 等关键治理属性在前端完全不可见、不可配置。

## What Changes

- **Namespace 列表表单重构**：将当前的简化单行表单改造为分组表单，按"基础信息 / ISO 治理属性 / 关联引用"三个 section 展示全部后端支持的可编辑字段。
  - 基础信息：中文名称、英文名称、定义（多语言 designations/definitions，使用 `DesignationEditor` / `DefinitionEditor` 已有组件）
  - ISO 治理属性：`shorthandPrefix`、`schemeReference`、`mandatoryNamingConventionIndicator`、`oneNamePerItemIndicator`、`oneItemPerNameIndicator`
  - 关联引用：`namingAuthorityId`（Organization 下拉选择）、`namingConventionIds`（NamingConvention 多选）、`parentId`（Namespace 下拉选择）
  - **不显示** Namespace 自身的标识符（`meta.identifier`），保持后端自动生成；也不引入 ScopedIdentifier 相关 UI（已确认单独需求处理）
- **Namespace 列表列扩展**：在现有"标识符/名称/状态"基础上新增 `shorthandPrefix`、`父命名空间`、`治理标志摘要`（三个 indicator 的可视化）三列。
- **读写路径对齐**：编辑入口拉取 `/api/v1/namespaces/{id}/detail`（`NamespaceDetailResponse`）以填充完整属性，新增/更新提交到 `/api/v1/namespaces`。
- **API 客户端补齐类型**：`metadata-registry-ui/src/api/resources.ts` 的 `namespaceApi` 由 `any` 改为强类型 `NamespaceResponse` / `NamespaceDetailResponse` / `NamespaceRequest`，对齐后端 DTO。
- **下拉候选项支持**：新增组织轻量候选项（Organization 列表）、命名规范轻量候选项（NamingConvention 列表，调用已有 `naming-conventions` 端点）、父命名空间候选项（Namespace 列表过滤掉自身）。
- **i18n 补齐**：`zh-CN.json` 中追加 `namespace.*`、`namingConvention.*` 命名空间下的所有字段名、tooltip、placeholder 文案。
- **新增 NamingConvention CRUD 页面**：补齐 `metadata-registry-ui/src/views/naming-convention/NamingConventionList.vue`（之前完全缺失），按 `RegistrationAuthorityList.vue` 同形态实现列表 + 表单，覆盖 `scopeRule`（必填）、`authorityRule`（可选）、`designations`（多语言，必填至少 1 条）、`definitions`、`registration` 全字段。配套新增左侧导航菜单项（与「命名空间」同级）。
- **交互行为**：
  - `mandatoryNamingConventionIndicator` 切换为 true 时，提示「已开启强制命名规范，必须至少绑定一个命名规范」并触发 `namingConventionIds` 校验。
  - `parentId` 选择时禁止选择自身及其后代（避免循环引用；后端也已禁止，前端做即时过滤）。
  - Namespace 删除前调用 `/namespaces/{id}/deletion-check`，按 `hasIdentifiers` 给用户清晰提示。
  - NamingConvention 列表中如果某条规范已被 Namespace 引用，删除时弹差异化提示（仅前端检查 `namingConventionIds` 引用关系；后端无强校验时给出确认提示）。

## Capabilities

### New Capabilities

- `namespace-ui-form`: 命名空间治理属性前端表单能力——按 ISO 11179-3:2023 暴露并校验所有后端支持的可编辑字段，包含多语言名称/定义、组织/命名规范/父命名空间下拉引用。
- `namespace-ui-list`: 命名空间列表展示与交互能力——列表列展示治理属性摘要、详情回填、删除前关联性检查、状态标识。
- `naming-convention-ui`: 命名规范（NamingConvention）CRUD 页面——列表 + 表单，覆盖 `scopeRule` / `authorityRule` / 多语言 `designations` / `definitions` / `registration` 全字段；同时为 Namespace 表单提供下拉数据源。

### Modified Capabilities

（无现有 spec 的需求级改动；前端 UI 改造不改变后端 spec 中的需求语义。）

## Impact

- `metadata-registry-ui/src/views/namespace/NamespaceList.vue` —— 主改造文件
- `metadata-registry-ui/src/views/naming-convention/NamingConventionList.vue` —— 新增（目录与文件均不存在）
- `metadata-registry-ui/src/api/resources.ts` —— `namespaceApi` 强类型化 + 新增 `namingConventionApi` 强类型 + 强类型 `namingConventionIds` 引用关系查询
- `metadata-registry-ui/src/i18n/zh-CN.json` —— 新增 `namespace.*` 与 `namingConvention.*` 文案段
- `metadata-registry-ui/src/router/index.ts`、`src/layouts/AppLayout.vue` —— 新增「命名规范」菜单项（与「命名空间」同级）
- 后端无改动（API 早已就位，仅消费既有 `NamespaceController` 与 `NamingConventionController` 端点）
