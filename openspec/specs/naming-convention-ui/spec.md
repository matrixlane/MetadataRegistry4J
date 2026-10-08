# naming-convention-ui Specification

## Purpose
提供「命名规范」（Naming_Convention，ISO/IEC 11179-3:2023 §8.4.4）的独立管理界面：按标准由注册标识识别、以五个规则属性（scope/authority/semantic/syntactic/lexical）为内容，支持完整 CRUD、搜索、删除前引用提示，并与命名空间页面解耦。指称通过 naming_convention_conformance 关联选择命名规范；命名规范表单保留指称/定义编辑区（Item 子类可被 named/defined，0..* 非必填）。

## Requirements

### Requirement: 命名规范必须可独立创建与管理

系统 MUST 提供「命名规范」管理页面，路径与「命名空间」同级，覆盖 `POST /api/v1/naming-conventions`、`GET /api/v1/naming-conventions`、`GET /api/v1/naming-conventions/{id}`、`PUT /api/v1/naming-conventions/{id}`、`DELETE /api/v1/naming-conventions/{id}` 完整 CRUD。命名规范按 ISO/IEC 11179-3:2023 §8.4.4 建模：由注册标识识别，内容为五个规则属性；创建 MUST NOT 要求填写指称或定义。

#### Scenario: 新建命名规范

- **WHEN** 用户打开「命名规范」页面，点击「新建」，填写 `scopeRule = "^[a-z]+$"` 与注册标识，提交
- **THEN** 系统调用 `POST /api/v1/naming-conventions` 提交 `NamingConventionRequest`，返回 201，列表中可见新行

#### Scenario: 编辑回填完整字段

- **WHEN** 用户点击列表中已有命名规范的「编辑」按钮
- **THEN** 系统调用 `GET /api/v1/naming-conventions/{id}` 拉取 `NamingConventionResponse`，表单回填 `scopeRule`、`authorityRule`、`semanticRule`、`syntacticRule`、`lexicalRule`、`designations`、`definitions` 与 `registration`，并保留 `meta.identifier` 一致

#### Scenario: 列表搜索

- **WHEN** 用户在搜索框输入关键词
- **THEN** 列表调用 `/api/v1/naming-conventions?q=<keyword>` 并刷新结果

### Requirement: 删除前关联性提示

删除按钮 MUST 在点击后扫描 Namespace 列表（最多 200 条），统计引用当前 NamingConvention 的 Namespace 数量，并以差异化弹窗提示用户。

#### Scenario: 无引用
- **WHEN** 没有 Namespace 引用该 NamingConvention
- **THEN** 弹窗显示默认确认「确认删除？」

#### Scenario: 存在引用
- **WHEN** 有 N 个 Namespace 引用该 NamingConvention
- **THEN** 弹窗提示「该命名规范已被 N 个命名空间引用，确认删除？」，二次确认后才调用 `DELETE /api/v1/naming-conventions/{id}`

#### Scenario: 引用关系查询上限
- **WHEN** Namespace 总数超过 200 条（前端扫描上限）
- **THEN** 弹窗提示「命名空间数量较多，建议使用搜索确认关联后操作」，不阻断删除

### Requirement: 与 Namespace 页面互不耦合

命名规范页面的实现 MUST NOT 依赖 Namespace 页面组件；Namespace 页面 MUST 仅通过 `namingConventionApi` 调用后端获取下拉候选项，不直接依赖命名规范页面的内部状态。

#### Scenario: 独立加载
- **WHEN** 用户单独打开「命名规范」页面（不打开命名空间页面）
- **THEN** 页面正常加载并展示列表，不触发 Namespace 相关接口调用

#### Scenario: Namespace 表单下拉候选项
- **WHEN** 用户在「命名空间」新建表单中打开 `namingConventionIds` 下拉
- **THEN** 下拉数据来源为 `GET /api/v1/naming-conventions?size=200`，不依赖「命名规范」页面是否打开

### Requirement: 列表列与表单字段

「命名规范」列表 MUST 展示以下列：
- **名称**（`label`（来自首选指称）；无指称时回退 `scopeRule` 截断文本）
- **scopeRule**（前 50 字符，超出截断）
- **authorityRule**（前 50 字符，超出截断；空时显示 `-`）
- **semanticRule**（前 50 字符，超出截断；空时显示 `-`）
- **syntacticRule**（前 50 字符，超出截断；空时显示 `-`）
- **lexicalRule**（前 50 字符，超出截断；空时显示 `-`）
- **状态**（`meta.registrationStatus`）

新建/编辑表单 MUST 提供 `scopeRule`（必填）、`authorityRule`、`semanticRule`、`syntacticRule`、`lexicalRule`（选填）五个规则属性输入，「注册标识」区段，以及「指称」「定义」编辑区（Naming_Convention 为 Item 子类，实例可被命名与定义；指称/定义 0..* 非必填）；字段提示 MUST 忠实对应 ISO/IEC 11179-3:2023 Table 17 的属性定义。

#### Scenario: 列渲染

- **WHEN** 列表加载完成
- **THEN** 按上述 7 列展示，每行数据来源于 `GET /api/v1/naming-conventions` 的 `NamingConventionResponse[]`

#### Scenario: 表单五规则属性

- **WHEN** 用户打开「新建命名规范」对话框并切换到「属性」页签
- **THEN** 表单呈现 scopeRule、authorityRule、semanticRule、syntacticRule、lexicalRule 五个输入项，scopeRule 标记必填

### Requirement: API 客户端强类型

`metadata-registry-ui/src/api/resources.ts` 的 `namingConventionApi` MUST 强类型化为 `NamingConventionRequest` / `NamingConventionResponse`，不允许使用 `any`。

#### Scenario: TypeScript 编译通过
- **WHEN** 开发者执行 `npm run build`
- **THEN** `namingConventionApi` 的入参/出参类型完整，无 `any` 类型警告

### Requirement: scopeRule 必填，designations 非必填

表单 MUST 强制 `scopeRule` 非空（与后端 `NamingConventionRequest` 的 `@NotBlank` 一致）；表单 MUST 提供指称与定义编辑区，MUST NOT 校验 designations/definitions 条数。

#### Scenario: scopeRule 为空提交

- **WHEN** 用户提交时 `scopeRule` 为空
- **THEN** 前端校验失败，提示「scopeRule 不能为空」

#### Scenario: 未填写指称提交

- **WHEN** 用户提交时未填写任何指称或定义
- **THEN** 系统正常提交，不提示 designations 相关错误
