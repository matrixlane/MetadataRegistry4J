# naming-convention-ui Delta

## MODIFIED Requirements

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

#### Scenario: 指称与定义编辑区

- **WHEN** 用户打开「新建命名规范」对话框
- **THEN** 表单存在「指称」与「定义」页签，各自提供多语言文本编辑（语言 + 文本 + 上下文关联）

### Requirement: scopeRule 必填，designations 非必填

表单 MUST 强制 `scopeRule` 非空（与后端 `NamingConventionRequest` 的 `@NotBlank` 一致）；表单 MUST 提供指称与定义编辑区，MUST NOT 校验 designations/definitions 条数。

#### Scenario: scopeRule 为空提交

- **WHEN** 用户提交时 `scopeRule` 为空
- **THEN** 前端校验失败，提示「scopeRule 不能为空」

#### Scenario: 未填写指称提交

- **WHEN** 用户提交时未填写任何指称或定义
- **THEN** 系统正常提交，不提示 designations 相关错误
