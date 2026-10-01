# individual-standard-compliance Specification

## Purpose
约束 `mdr.individual` 表的列集必须与 ISO/IEC 11179-3:2023 §6.3.2 定义的 Individual 类属性严格一致，并规定非标准应用属性（avatar）的归宿为 ext schema（ext.account）及其账号级语义、API 契约与数据搬迁规则。

## Requirements

### Requirement: mdr.individual 列集与标准严格一致

`mdr.individual` 表 SHALL 仅包含 ISO/IEC 11179-3:2023 §6.3.2 Table 1 定义的属性对应列（name、title、mail_address、email_address、phone_number，其中 email_address/phone_number 为集合表）与内部标识 id 列。MUST NOT 存在标准未定义的其他列。

#### Scenario: 标准符合性核查

- WHEN 对 `mdr.individual` 执行列清单核查
- THEN 列集与 §6.3.2 Table 1 + id 列一致，无 identifier 列、无 avatar 列

### Requirement: avatar 归宿为 ext.account

用户头像 SHALL 存储于 `ext.account.avatar`（SQL Server）/ `account.avatar`（SQLite，无 schema 概念），作为账号级应用扩展属性。MUST NOT 存储于 `mdr.individual`。

#### Scenario: 头像存储宿主

- WHEN 任意账号上传头像
- THEN 数据写入该账号行的 avatar 列，individual 表无 avatar 列可写

### Requirement: 头像与 Individual 生命周期解耦

上传/更新/删除头像 MUST NOT 创建、修改或要求存在 Individual 记录；账号无 Individual 时头像功能 SHALL 完整可用。

#### Scenario: 无 Individual 账号上传头像

- WHEN 账号未关联 Individual 的用户上传头像
- THEN 头像保存成功并回显，且不产生新的 Individual 记录

### Requirement: 头像 API 契约

头像 SHALL 通过 `GET/PUT/DELETE /auth/me/avatar` 访问。原 `/auth/me/individual/avatar` 路径 MUST NOT 继续存在。

#### Scenario: 端点访问

- WHEN 客户端请求 `PUT /auth/me/avatar`
- THEN 保存当前登录账号的头像并返回成功；请求原 `/auth/me/individual/avatar` 路径返回 404

### Requirement: Individual 管理 API 无 identifier 契约

`IndividualRequest`/`IndividualResponse` MUST NOT 包含 identifier 字段；管理端列表与表单 MUST NOT 展示或提交 identifier。

#### Scenario: 管理端 Individual 接口契约

- WHEN 客户端调用 Individual 管理接口创建/更新/查询
- THEN 请求与响应中均无 identifier 字段，列表页无标识符列与表单项

### Requirement: 迁移期存量头像数据搬迁

应用迁移时 SHALL 将 `individual.avatar` 按 `account.individual_id` 关联复制到 `account.avatar` 后再删除原列，三方言（SQLite/SQL Server/达梦）语义一致且幂等。

#### Scenario: 存量数据搬迁

- WHEN 应用 `V20260930001` 迁移（存在已绑定账号且有个体头像的存量数据）
- THEN 对应账号行的 avatar 等于原 Individual 的 avatar；`individual` 表的 avatar/identifier 两列被删除
