# individual-identifier — Individual 标识符的存储、生成与展示联动

## Purpose

补全 Individual 标识符（应用扩展属性，非 ISO/IEC 11179-3 标准属性）的端到端契约：字段存储、留空自动生成、请求/响应透传、列表搜索，以及 Registrar 名称展示的回填联动。

## ADDED Requirements

### Requirement: 标识符字段存储

系统 SHALL 在 `Individual` 实体提供可空 `identifier` 字符串属性（应用扩展属性），并提供三方言迁移：SQLite/SQL Server `varchar(255)`、达梦 `varchar(255)`，对 `individual` 表 `add column`。该字段 MUST NOT 有唯一约束。

#### Scenario: 迁移后新增列

- **WHEN** 任一支持方言执行迁移 `V20260917001__Individual_identifier`
- **THEN** `individual` 表存在可空 `identifier` 列，既有行该列为空

### Requirement: 标识符自动生成

管理端（`IndividualService` 创建/更新）当请求未提供非空 identifier 且实体 identifier 为空时，MUST 生成 `individual-{8位小写hex}`。资料页（`AuthService.updateMyIndividual`）首次创建或 identifier 为空时，MUST 生成 `{username}-individual`。

#### Scenario: 管理端留空创建

- **WHEN** 创建 Individual 时 identifier 留空
- **THEN** 持久化的 identifier 形如 `individual-` 加 8 位十六进制

#### Scenario: 资料页首次保存

- **WHEN** 用户首次在"我的资料"保存个人信息
- **THEN** 其 Individual 的 identifier 为 `{登录用户名}-individual`

### Requirement: 请求响应透传与搜索

`IndividualRequest`/`IndividualResponse` MUST 包含 `identifier` 字段；`IndividualService.list` 的关键词搜索 MUST 覆盖 identifier、姓名、职务三列。

#### Scenario: 按标识符搜索

- **WHEN** 列表关键词查询匹配某 Individual 的 identifier
- **THEN** 该 Individual 出现在结果中

### Requirement: Registrar 名称展示回填

保存个人资料（`updateMyIndividual`）成功后，系统 MUST 将当前账号绑定的、`individual` 为空的 Registrar 回填到该 Individual；MUST NOT 覆盖 Registrar 已有的 Individual 引用。注册人员列表的名称列 MUST 按 `individualName || organizationName || identifier` 顺序取值显示。

#### Scenario: 账号绑定 registrar 未关联 Individual

- **WHEN** 用户首次保存个人资料，且其账号绑定的 registrar 的 individual 为空
- **THEN** 该 registrar 关联到新 Individual，注册人员列表名称列显示该个人姓名

#### Scenario: registrar 已关联其他 Individual

- **WHEN** 用户保存个人资料，但 registrar 已有关联的 Individual
- **THEN** 原引用保持不变
