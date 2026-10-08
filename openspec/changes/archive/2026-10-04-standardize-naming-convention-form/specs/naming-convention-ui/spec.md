# naming-convention-ui Delta

## MODIFIED Requirements

### Requirement: 命名规范必须可独立创建与管理

系统 MUST 提供「命名规范」管理页面，路径与「命名空间」同级，覆盖 `POST /api/v1/naming-conventions`、`GET /api/v1/naming-conventions`、`GET /api/v1/naming-conventions/{id}`、`PUT /api/v1/naming-conventions/{id}`、`DELETE /api/v1/naming-conventions/{id}` 完整 CRUD。命名规范按 ISO/IEC 11179-3:2023 §8.4.4 建模：由注册标识识别，内容为五个规则属性；创建 MUST NOT 要求填写指称或定义。

#### Scenario: 新建命名规范

- **WHEN** 用户打开「命名规范」页面，点击「新建」，填写 `scopeRule = "^[a-z]+$"` 与注册标识，提交
- **THEN** 系统调用 `POST /api/v1/naming-conventions` 提交 `NamingConventionRequest`（不含 designations/definitions），返回 201，列表中可见新行

#### Scenario: 编辑回填完整字段

- **WHEN** 用户点击列表中已有命名规范的「编辑」按钮
- **THEN** 系统调用 `GET /api/v1/naming-conventions/{id}` 拉取 `NamingConventionResponse`，表单回填 `scopeRule`、`authorityRule`、`semanticRule`、`syntacticRule`、`lexicalRule` 与 `registration`，并保留 `meta.identifier` 一致

#### Scenario: 列表搜索

- **WHEN** 用户在搜索框输入关键词
- **THEN** 列表调用 `/api/v1/naming-conventions?q=<keyword>` 并刷新结果

### Requirement: 列表列与表单字段

「命名规范」列表 MUST 展示以下列：
- **名称**（`label`（来自首选指称）；无指称时回退 `scopeRule` 截断文本）
- **scopeRule**（前 50 字符，超出截断）
- **authorityRule**（前 50 字符，超出截断；空时显示 `-`）
- **semanticRule**（前 50 字符，超出截断；空时显示 `-`）
- **syntacticRule**（前 50 字符，超出截断；空时显示 `-`）
- **lexicalRule**（前 50 字符，超出截断；空时显示 `-`）
- **状态**（`meta.registrationStatus`）

新建/编辑表单 MUST 提供 `scopeRule`（必填）、`authorityRule`、`semanticRule`、`syntacticRule`、`lexicalRule`（选填）五个规则属性输入，以及「注册标识」区段；表单 MUST NOT 包含指称或定义编辑区；字段提示 MUST 忠实对应 ISO/IEC 11179-3:2023 Table 17 的属性定义。

#### Scenario: 列渲染

- **WHEN** 列表加载完成
- **THEN** 按上述 7 列展示，每行数据来源于 `GET /api/v1/naming-conventions` 的 `NamingConventionResponse[]`

#### Scenario: 表单五规则属性

- **WHEN** 用户打开「新建命名规范」对话框并切换到「属性」页签
- **THEN** 表单呈现 scopeRule、authorityRule、semanticRule、syntacticRule、lexicalRule 五个输入项，scopeRule 标记必填

## REMOVED Requirements

### Requirement: scopeRule 必填与 designations 至少一条

**Reason**: 命名规范按 ISO/IEC 11179-3:2023 §8.4.4 由注册标识识别、内容为五个规则属性；指称/定义不属于命名规范编辑界面，designations 必填校验随之移除（由「scopeRule 必填，designations 非必填」需求替代）。

## ADDED Requirements

### Requirement: scopeRule 必填，designations 非必填

表单 MUST 强制 `scopeRule` 非空（与后端 `NamingConventionRequest` 的 `@NotBlank` 一致）；表单 MUST NOT 包含指称或定义编辑区，MUST NOT 校验 designations 条数。

#### Scenario: scopeRule 为空提交

- **WHEN** 用户提交时 `scopeRule` 为空
- **THEN** 前端校验失败，提示「scopeRule 不能为空」

#### Scenario: 未填写指称提交

- **WHEN** 用户提交时未填写任何指称或定义
- **THEN** 系统正常提交，不提示 designations 相关错误
