# registrar-standard-compliance Specification

## Purpose
约束 `mdr.registrar` 表不得包含 ISO/IEC 11179-3:2023 §6.3.5 Contact 语义之外的属性列，并规定 Registrar 管理 API 与 UI 无 identifier 契约；同时清除 Contact 管理页无契约对应的标识符幽灵字段。

## Requirements

### Requirement: mdr.registrar 列集与标准语义一致

`mdr.registrar` 表 MUST NOT 包含非标准属性列。除内部标识 id 列外，仅允许承载 Registrar 子类相对 Contact 的标准扩展语义列（registration_authority_id 关联）。MUST NOT 存在 identifier 列。

#### Scenario: 列集核查

- WHEN 对 `mdr.registrar` 执行列清单核查
- THEN 无 identifier 列，列集与 Contact §6.3.5 语义 + Registrar 子类 RA 关联一致

### Requirement: Registrar 管理 API 无 identifier 契约

`RegistrarRequest` MUST NOT 包含 identifier 字段（含其必填校验）；`RegistrarResponse` MUST NOT 包含 identifier 字段。

#### Scenario: 管理端 Registrar 接口契约

- WHEN 客户端调用 `/api/v1/registrars` 创建/更新/查询
- THEN 请求体无需 identifier 且响应体无 identifier 字段

### Requirement: 管理端 Registrar/Contact 页面无标识符

Registrar 列表页 MUST NOT 展示或要求填写标识符；Contact 列表页 MUST NOT 展示或要求填写标识符（该字段本就无后端契约）。

#### Scenario: 页面渲染与表单

- WHEN 用户打开管理端 Registrar 或 Contact 列表页
- THEN 表格无标识符列，新建/编辑表单无标识符表单项，数据加载与保存正常

### Requirement: 迁移删列三方言一致

`V20261001001` 迁移 SHALL 在三方言（SQLite / SQL Server / 达梦）删除 `registrar.identifier` 列，语义一致并带幂等守卫（守卫风格与 V20260930001 相同）。

#### Scenario: 应用迁移

- WHEN 在任一支持方言执行 `V20261001001`
- THEN `registrar` 表 identifier 列被删除；重复执行被守卫跳过或按 Flyway 一次性语义报错于重复 DDL
