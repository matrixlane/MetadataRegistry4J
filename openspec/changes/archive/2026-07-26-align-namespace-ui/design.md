## Context

后端在 `align-namespace-iso11179-3` 提案（commit 2077fd6）完成后，`NamespaceRequest/Response/DetailResponse` 已承载完整的 ISO/IEC 11179-3:2023 治理属性：`shorthandPrefix`、`schemeReference`、`mandatoryNamingConventionIndicator`、`oneNamePerItemIndicator`、`oneItemPerNameIndicator`、`namingAuthorityId`、`namingConventionIds`、`parentId`，并通过 `NamespaceConstraintValidator` 在 Service 层做强制校验（强制命名规范、单名/单名约束）。

但前端 `metadata-registry-ui/src/views/namespace/NamespaceList.vue` 仍是早期版本：表单只有"标识符 / 中文名称 / 英文名称"三个字段，提交体为旧的 `{ identifier, titleZh, titleEn }` 平铺结构，与后端 `NamespaceRequest` 要求的复合结构（`designations: TextLine[]` + `registration: ItemRegistrationRequest` + 8 个治理字段）不兼容，新增/编辑请求会被 `@NotEmpty` 校验拦截。

`openspec/changes/align-namespace-iso11179-3/tasks.md` 的「8. 可选后续」第 8.1 项「前端表单增加 Namespace 治理属性输入字段」从合入时（2026-07-24）至今未执行。组件库（Element Plus 2.x）和通用编辑器组件（`DesignationEditor`、`DefinitionEditor`、`DataTablePage`、`ResourceFormDialog`）均已就位，本设计直接复用。

## Goals / Non-Goals

**Goals:**
- 在不引入新后端改动的前提下，将 `NamespaceList.vue` 改造为按 ISO 治理属性全字段录入与展示。
- 表单分组清晰：基础信息（名称/定义）、ISO 治理属性（prefix/scheme/三个 indicator）、关联引用（namingAuthority/parent/namingConventions）。
- 编辑时拉取 `NamespaceDetailResponse`，新增/更新走现有 `POST/PUT /api/v1/namespaces`。
- 列表列展示治理属性摘要（shorthandPrefix、parent、indicator 标签）。
- 删除前调用 `/api/v1/namespaces/{id}/deletion-check`，按 `hasIdentifiers` 提示用户。
- 不显示 Namespace 自身标识符（标识符由后端自动生成于 `meta.identifier`），也不引入 ScopedIdentifier 相关 UI。

**Non-Goals:**
- 不修改后端任何代码或 DTO。
- 不引入 ScopedIdentifier 录入/查看入口（由其他需求处理）。
- 不引入 NamingConvention 的全功能 CRUD 页面（仅消费既有 `GET /api/v1/naming-conventions` 作为下拉候选项数据源；NamingConvention 完整 CRUD 单独排期）。
- 不引入 RegistrationAuthority 与 Namespace 的双向选择 UI（DTO 故意不暴露 `registrationAuthorityId`，由后端内部维护）。
- 不实现后端标准的注册工作流按钮（提交/批准/退役等生命周期操作），仅做字段级 CRUD。

## Decisions

### D1: 表单分组 + 自适应弹窗宽度
**决策**：将 `ResourceFormDialog` 实例化为 `width="720px"`（从默认 560px 加宽以容纳三个 section），内部用 `<el-tabs>` 或 `<el-collapse>` 分组。  
**理由**：与现有 `DataTablePage` 一致的「窄列多分组」风格；`ItemFormDialog.vue` 已使用分组模式（见 `DesignationDefinitionSection`），可直接参考样式。  
**备选**：每个 section 用独立 dialog（放弃：交互割裂、用户操作多）；单长表单（放弃：560px 装不下，会出现横向滚动）。

### D2: 多语言名称/定义直接复用 `DesignationEditor` / `DefinitionEditor`
**决策**：表单中 designations/definitions 字段使用既有 `<DesignationEditor v-model="form.designations" />` 和 `<DefinitionEditor v-model="form.definitions" />`。  
**理由**：组件已支持语言下拉、可添加多行、上下文关联（designation 用），与 NamespaceService 后端期望的 `List<TextLine>` 一致（见 `DesignationEditor` 的 `TextLineForm` 类型）。  
**备选**：自建简化 textarea 多行（放弃：放弃既有上下文关联能力，且会与全项目其他 Item 表单不一致）。

### D3: 强类型化 `namespaceApi`
**决策**：在 `metadata-registry-ui/src/api/resources.ts` 中新增 `interface NamespaceResponse / NamespaceDetailResponse / NamespaceRequest`，替换 `namespaceApi` 中的 `any` 泛型。  
**理由**：避免后端 DTO 字段变化时静默丢失；与全项目逐步类型化趋势一致（`appUserApi`、`contextApi` 等仍部分用 `any`，但本 change 是新接触此模块的合适入口）。  
**备选**：保留 `any`（放弃：暴露原始问题到运行时才发现）。

### D4: 下拉候选项用轻量列表端点，不新增 options 接口
**决策**：
- Organization 候选项：调用 `GET /api/v1/organizations?size=200`（与 `organizationApi` 一致）
- NamingConvention 候选项：调用 `GET /api/v1/naming-conventions?size=200`（既有 `naming-conventions` 端点）
- 父命名空间候选项：调用 `GET /api/v1/namespaces?size=200` 后在客户端过滤掉自身及其后代

**理由**：后端暂未提供 `options` 端点；轻量列表全量拉取（200 条封顶）对当前数据规模足够；与项目内「单实体」的下拉候选项模式一致（如 `ContextSelector`）。  
**备选**：新加 `/api/v1/namespaces/options`、`/api/v1/naming-conventions/options` 端点（放弃：本 change 主张不动后端；待数据规模扩大再优化）。

### D5: 不显示 Namespace 标识符字段
**决策**：表单中省略 `meta.identifier` 输入；列表列保留「标识符」列（只读展示，由后端生成）。  
**理由**：用户明确要求。  
**备选**：保留可编辑（放弃：与用户决策冲突；后端标识符通常由后端策略生成，不应前端覆盖）。

### D6: 循环引用防护
**决策**：父命名空间候选项在客户端排除「当前 namespace 自身」以及「所有 `parentId` 链最终指向自身的候选」；同时在提交前做同样的二次校验。  
**理由**：避免后端 `NamespaceConstraintValidator` 抛「循环引用」错误带来更差的 UX；客户端即时反馈更友好。  
**备选**：仅依赖后端校验（放弃：UX 差，错误才反馈）。

### D7: 三个 indicator 用 `el-switch`
**决策**：三个布尔 indicator 在表单中用 `el-switch` 展示，列表列中用 `el-tag`（true 时蓝色，false 时灰色）。  
**理由**：与项目内布尔类 UI 风格一致；表格列展示时摘要化。  
**备选**：checkbox（放弃：现代项目更倾向 switch 视觉）。

### D8: 强制命名规范开关联动校验
**决策**：`mandatoryNamingConventionIndicator` 切换为 `true` 时，前端用 `el-form` 规则要求 `namingConventionIds.length >= 1`，并通过 `el-alert` 提示用户。  
**理由**：让前端校验与后端 `NamespaceConstraintValidator` 的 `validateMandatoryNamingConvention` 行为保持一致，避免用户「开关开了但没绑定 → 提交失败」。  
**备选**：仅依赖后端校验（放弃：同上 UX 差）。

### D9: 删除前关联性检查
**决策**：点击删除按钮时，调用 `GET /api/v1/namespaces/{id}/deletion-check`（已有），若 `hasIdentifiers === true` 则弹 `ElMessageBox.confirm` 告知用户「该命名空间下还有 N 个标识符，确认一并删除？」（按业务要求，参考 `DeletionCheckResponse.message` 文案）。  
**理由**：现有 `deletionCheck` API 已经在 `namespaceApi` 中暴露，本 change 直接消费。  
**备选**：直接删（放弃：可能产生悬空标识符，与 `NamespaceConstraintValidator` 行为不一致）。

### D10: NamingConvention UI 沿用 RegistrationAuthority/Organization 同形态

**决策**：`metadata-registry-ui/src/views/naming-convention/NamingConventionList.vue` 完全按 `RegistrationAuthorityList.vue` 的结构写：`<DataTablePage>` + `<ResourceFormDialog>`，列表列展示 `id/label/scopeRule/authorityRule`，表单分基础信息与多语言录入两块。  
**理由**：项目内同类「Administered Item」CRUD 页面形态高度一致（参见 `OrganizationList.vue`、`RegistrationAuthorityList.vue`、`ContextList.vue`），沿用既保证视觉统一也最大化代码复用。  
**备选**：仿照 Namespace 改造后的「分组 + tabs」结构（放弃：NamingConvention 字段少，分组无意义反而增加噪音）。

### D11: NamingConvention 表单必填校验与后端 DTO 对齐

**决策**：
- `scopeRule` 必填（`@NotBlank`），用 `el-input` + `required` rule
- `designations` 至少 1 条（`@NotEmpty`），通过 `<DesignationEditor>` 控件的「至少 1 行」校验保证
- `authorityRule` 可选
- `definitions` 可选
- 提交时携带完整 `NamingConventionRequest`（含 `registration`）

**理由**：与后端 `NamingConventionRequest` 注解一致；不重复造 `Registration` 子表单，复用 `ItemFormDialog` 思路但本 change 不引入新 dialog 组件——直接复用 `ResourceFormDialog` + 多语言编辑器。  
**备选**：自建一个 `NamingConventionFormDialog.vue`（放弃：增加组件但不增加能力，违反复用原则）。

### D12: NamingConvention 列表「被引用」提示

**决策**：NamingConvention 列表的删除按钮点击后，前端调 `GET /api/v1/namespaces?size=200` 在客户端过滤 `namingConventionIds` 包含当前 id 的所有 namespace，统计数量并以 `ElMessageBox.confirm` 弹「该命名规范已被 N 个命名空间引用，确认删除？」（注意：NamingConvention 删除是硬删，关联表会清空）。  
**理由**：后端无 NamingConvention 删除前的关联性检查 API，差量保护责任在前端；后端 cascade 行为已通过 `align-namespace-iso11179-3` 测试覆盖。  
**备选**：直接删（放弃：UX 差；用户在大量引用情况下容易误删）。

### D13: 菜单导航接入

**决策**：在 `src/router/index.ts` 与 `src/layouts/AppLayout.vue` 中按现有「注册通用设施」分组（参考 `standardize-menu-layout` change），新增「命名规范」菜单项，与「命名空间」同级；菜单项 i18n key 为 `nav.namingConvention`。  
**理由**：不破坏现有菜单布局；用户可从顶部菜单直达页面，Namespace 表单的下拉候选项也有完整管理入口。  
**备选**：放在「系统设置」二级菜单（放弃：与命名空间同级概念应当并列展示）。

## Risks / Trade-offs

- **R1: 候选项数据量增长后端拉取 200 条会变慢** → 限制为前 200 条并在 UI 上注明「数据过多时建议使用搜索」，未来加 `/options` 端点。
- **R2: 多语言编辑器（DesignationEditor）目前对 Namespace 没有专门适配** → 复用即用，如发现 Namespace 特有需求（无 contexts 字段）再回退到简化版。
- **R3: 编辑器中无 `meta.identifier` 字段，编辑回填到表单时如果不携带，后端 `ItemRegistrationRequest` 缺失 `identifier` 会失败** → 拉取 `/detail` 时记录 `meta.identifier` 到本地 form 副本（不显示在 UI 但提交时带上）。
- **R4: NamingConvention 端点 `/api/v1/naming-conventions` 还没在前端 API 客户端中暴露** → 需在本 change 中补全（见 tasks.md T1.3）。
- **R5: 多语言编辑器对 languageId 强依赖 `Language` 表数据** → 已有 `languageApi.list`，与 DesignationEditor 一致，无需额外工作。
- **R6: NamingConvention 删除的「被引用」检查需全量拉取 namespace 列表（最多 200 条）** → 超过 200 条时前端给出"请使用搜索定位"，并提示后端日后补 `naming-conventions/{id}/deletion-check` 端点。

## Migration Plan

无数据库迁移（仅前端改动）。部署步骤：

1. 前端构建：`npm run build`，产物覆盖 `metadata-registry-web/src/main/resources/static/`。
2. 后端重新打包：`./mvnw.cmd -pl metadata-registry-web package`，重启服务。
3. 回归测试：
   - 在「命名空间」列表页：
     - 新建：填写中文/英文名、绑定 Organization、绑定 1 个 NamingConvention、开启 `mandatoryNamingConventionIndicator`、提交 → 列表应能展示新行。
     - 编辑：点击编辑 → 表单应回填完整字段 → 改动某 indicator → 保存 → 列表与详情应同步。
     - 删除：尝试删除一个有子 NS 的父 NS → 应有友好提示。
   - 在「命名规范」列表页：
     - 新建：填写 `scopeRule`、1 条 designation → 提交 → 列表可见。
     - 编辑：点击编辑 → 表单回填 → 改动 `authorityRule` → 保存 → 列表与详情同步。
     - 删除：删除一个被某 Namespace 引用的 NC → 应有「被 N 个命名空间引用」友好提示。
4. 不需要回滚预案（仅前端 UI 改造，不影响后端数据）。

## Open Questions

- OQ1：用户对「列表列」是否要展示 `mandatoryNamingConventionIndicator` 等 indicator 的标签数 vs 文字描述 有无偏好？暂以三个 el-tag 摘要展示（每个 indicator 单独 tag）。
- OQ2：父命名空间下拉是否要支持搜索？本 change 暂以全量列表展示（200 条），如确认需要可升级为 `el-select` + `filterable`。
- OQ3：NamingConvention 列表是否要支持"按是否被引用过滤"？本 change 暂以纯文本搜索框过滤（q 参数），不做引用过滤。
