# Design: expose-organization-attributes-in-ra-form

## Context

`Organization`（§6.3.3 表 2）属性：name（1..*）、mail_address（0..1）、email_address（0..*）、phone_number（0..*）、web_address（0..1）。实体为 JOINED 继承，RA 创建即组织创建（organization + registration_authority 两行同事务）。后端 DTO/applyRequest 已映射全部字段。

## Decisions

- 多值字段（邮箱/电话）用 el-select `multiple filterable allow-create default-first-option` 标签输入，提交为 string[]。
- 编辑回填直接复用列表响应（已含 mailAddress/emailAddress/phoneNumber/webAddress）。
- 必填策略不变：仅名称与复合标识两段必填，组织联系属性全部选填（标准 0..* / 0..1）。

## Risks / Trade-offs

- [无]：纯界面补全 + 测试，API 契约不变。
