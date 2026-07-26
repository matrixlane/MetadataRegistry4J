## Purpose

定义「命名规范」（NamingConvention）管理页面的能力边界：列表必须按 ISO/IEC 11179-3:2023 的 `Naming_Convention` 属性（`scopeRule`、`authorityRule`、多语言 designations/definitions）进行展示与编辑；表单必须与后端 `NamingConventionRequest` DTO 字段完全对齐。

## ADDED Requirements

### Requirement: 命名规范必须可独立创建与管理

系统 MUST 提供「命名规范」管理页面，路径与「命名空间」同级，覆盖 `POST /api/v1/naming-conventions`、`GET /api/v1/naming-conventions`、`GET /api/v1/naming-conventions/{id}`、`PUT /api/v1/naming-conventions/{id}`、`DELETE /api/v1/naming-conventions/{id}` 完整 CRUD。

#### Scenario: 新建命名规范
- **WHEN** 用户打开「命名规范」页面，点击「新建」，填写 `scopeRule = "^[a-z]+$"`，添加 1 条 designation（语言 zh-CN，文本「小写命名规则」），提交
- **THEN** 系统调用 `POST /api/v1/naming-conventions` 提交 `NamingConventionRequest`，返回 201，列表中可见新行

#### Scenario: 编辑回填完整字段
- **WHEN** 用户点击列表中已有命名规范的「编辑」按钮
- **THEN** 系统调用 `GET /api/v1/naming-conventions/{id}` 拉取 `NamingConventionResponse`，表单回填 `scopeRule`、`authorityRule`、`designations`、`definitions`、`registration`，并保留 `meta.identifier` 一致

#### Scenario: 列表搜索
- **WHEN** 用户在搜索框输入关键词
- **THEN** 列表调用 `/api/v1/naming-conventions?q=<keyword>` 并刷新结果

### Requirement: scopeRule 必填与 designations 至少一条

表单 MUST 强制 `scopeRule` 非空、`designations` 至少一条，与后端 `NamingConventionRequest` 的 `@NotBlank` / `@NotEmpty` 注解保持一致。

#### Scenario: scopeRule 为空提交
- **WHEN** 用户提交时 `scopeRule` 为空
- **THEN** 前端校验失败，提示「scopeRule 不能为空」

#### Scenario: designations 列表为空提交
- **WHEN** 用户提交时未添加任何 designation
- **THEN** 前端校验失败，提示「designations 不能为空」

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
- **标识符**（`id` 截取前 8 位）
- **首选名称**（`label`，来自首选 designation）
- **scopeRule**（前 50 字符，超出截断）
- **authorityRule**（前 50 字符，超出截断；空时显示 `-`）
- **状态**（`meta.registrationStatus`）

#### Scenario: 列渲染
- **WHEN** 列表加载完成
- **THEN** 按上述 5 列展示，每行数据来源于 `GET /api/v1/naming-conventions` 的 `NamingConventionResponse[]`

### Requirement: API 客户端强类型

`metadata-registry-ui/src/api/resources.ts` 的 `namingConventionApi` MUST 强类型化为 `NamingConventionRequest` / `NamingConventionResponse`，不允许使用 `any`。

#### Scenario: TypeScript 编译通过
- **WHEN** 开发者执行 `npm run build`
- **THEN** `namingConventionApi` 的入参/出参类型完整，无 `any` 类型警告
