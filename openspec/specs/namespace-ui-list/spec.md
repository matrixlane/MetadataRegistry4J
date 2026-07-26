# namespace-ui-list Specification

## Purpose
TBD - created by archiving change align-namespace-ui. Update Purpose after archive.
## Requirements
### Requirement: 列表按 ISO 治理属性展示摘要

「命名空间」列表页 MUST 展示以下列：
- **标识符**（只读，来源于 `meta.identifier`）
- **名称**（来源于 `label` 或 `designations[0].value`）
- **简写前缀**（`shorthandPrefix`）
- **父命名空间**（`parentLabel`）
- **治理标志摘要**（三个 indicator，每个为独立 `el-tag`，true 时高亮）
- **状态**（来源于 `meta.registrationStatus`）

#### Scenario: 列表渲染
- **WHEN** 用户打开「命名空间」列表页
- **THEN** 列表按上述 6 列展示，每行数据来源于 `GET /api/v1/namespaces` 的 `NamespaceResponse[]`

#### Scenario: 治理标志可视化
- **WHEN** 某 Namespace 的 `mandatoryNamingConventionIndicator = true`
- **THEN** 该行「治理标志摘要」列展示一个高亮（蓝色）的 `el-tag` 文案「强制命名规范」

### Requirement: 列表支持新建/编辑/删除

列表页 MUST 在工具栏提供「新建」按钮，并在每行操作列提供「编辑」「删除」按钮。点击「编辑」复用 `namespace-ui-form` 的编辑模式；点击「删除」按 `namespace-ui-form` 的「删除前关联性检查」流程执行。

#### Scenario: 新建按钮可见
- **WHEN** 当前部署模式允许新建命名空间（参照 `RegistrationAuthorityList.vue` 的 `canCreate` 门控模式）
- **THEN** 工具栏「新建」按钮可见

#### Scenario: 编辑入口
- **WHEN** 用户点击行内「编辑」按钮
- **THEN** 打开 `ResourceFormDialog` 并通过 `namespaceApi.detail(id)` 回填完整字段

### Requirement: 列表与详情数据契约

列表查询 MUST 调用 `GET /api/v1/namespaces` 接收 `NamespaceResponse` 列表；编辑回填 MUST 调用 `GET /api/v1/namespaces/{id}/detail` 接收 `NamespaceDetailResponse`。前端 API 客户端 `namespaceApi` MUST 提供 `list` / `detail` / `create` / `update` / `delete` / `deletionCheck` 六个方法，且 MUST 强类型化（不再使用 `any`）。

#### Scenario: 强类型化 API
- **WHEN** TypeScript 编译
- **THEN** `metadata-registry-ui/src/api/resources.ts` 中 `namespaceApi` 的入参/出参类型为 `NamespaceRequest` / `NamespaceResponse` / `NamespaceDetailResponse` 的 TS interface，不出现 `any`

#### Scenario: 列表请求 q 参数生效
- **WHEN** 用户在搜索框输入关键词
- **THEN** 列表调用 `/api/v1/namespaces?q=<keyword>` 并刷新结果

